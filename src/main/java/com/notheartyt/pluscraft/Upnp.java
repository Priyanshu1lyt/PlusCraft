package com.notheartyt.pluscraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatic router port forwarding via UPnP (SSDP discovery + SOAP). No server needed:
 * the host's own router is asked to forward the world's TCP port to this PC.
 */
public final class Upnp {
    public static volatile String status = "Not started";
    public static volatile String externalIp = null;
    public static volatile boolean busy = false;
    public static volatile int mappedPort = -1;

    private static String controlUrl;
    private static String serviceType;
    private static String localIp;

    private Upnp() {}

    public static void openAsync(int port) {
        if (busy) return;
        busy = true;
        status = "Contacting router...";
        Thread t = new Thread(() -> {
            try {
                if (mappedPort > 0 && mappedPort != port) closeBlocking();
                discover();
                try {
                    addMapping(port, 0);
                } catch (IOException first) {
                    addMapping(port, 7200); // some routers refuse infinite leases
                }
                mappedPort = port;
                String ip = null;
                try {
                    Matcher m = Pattern.compile("<NewExternalIPAddress>(.*?)</NewExternalIPAddress>")
                            .matcher(soap("GetExternalIPAddress", ""));
                    if (m.find()) ip = m.group(1).trim();
                } catch (Exception ignored) {
                }
                externalIp = (ip == null || ip.isEmpty()) ? null : ip;
                if (externalIp != null && isPrivate(externalIp)) {
                    status = "Port open, but your ISP uses CGNAT - friends cannot reach you directly.";
                } else {
                    status = "Port " + port + " forwarded automatically.";
                }
            } catch (Exception e) {
                status = "UPnP failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            } finally {
                busy = false;
            }
        }, "PlusCraft-UPnP");
        t.setDaemon(true);
        t.start();
    }

    public static void closeAsync() {
        if (busy || mappedPort <= 0) return;
        busy = true;
        Thread t = new Thread(() -> {
            try {
                closeBlocking();
            } finally {
                busy = false;
            }
        }, "PlusCraft-UPnP-close");
        t.setDaemon(true);
        t.start();
    }

    /** Removes the router mapping (safe to call from a shutdown hook). */
    public static void closeBlocking() {
        int p = mappedPort;
        if (p <= 0 || controlUrl == null) return;
        try {
            soap("DeletePortMapping", "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + p
                    + "</NewExternalPort><NewProtocol>TCP</NewProtocol>");
        } catch (Exception ignored) {
        }
        mappedPort = -1;
        externalIp = null;
        status = "Port forward closed.";
    }

    // ------------------------------------------------------------ internals

    private static void discover() throws Exception {
        String req = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n"
                + "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n";
        byte[] data = req.getBytes(StandardCharsets.US_ASCII);
        Pattern loc = Pattern.compile("(?im)^location:\\s*(\\S+)");
        try (DatagramSocket s = new DatagramSocket()) {
            s.setSoTimeout(2500);
            s.send(new DatagramPacket(data, data.length, InetAddress.getByName("239.255.255.250"), 1900));
            long end = System.currentTimeMillis() + 3500;
            while (System.currentTimeMillis() < end) {
                DatagramPacket p = new DatagramPacket(new byte[2048], 2048);
                try {
                    s.receive(p);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String resp = new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII);
                Matcher m = loc.matcher(resp);
                if (m.find() && fetchControl(m.group(1))) return;
            }
        }
        throw new IOException("No UPnP router found (UPnP may be turned off in your router)");
    }

    private static boolean fetchControl(String location) throws Exception {
        URI base = URI.create(location);
        String xml = http(base.toURL(), "GET", null, null);
        Matcher m = Pattern.compile(
                "<serviceType>(urn:schemas-upnp-org:service:WAN(?:IP|PPP)Connection:\\d)</serviceType>.*?<controlURL>(.*?)</controlURL>",
                Pattern.DOTALL).matcher(xml);
        if (!m.find()) return false;
        serviceType = m.group(1);
        controlUrl = base.resolve(m.group(2).trim()).toString();
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName(base.getHost()), 1900);
            localIp = s.getLocalAddress().getHostAddress();
        }
        return true;
    }

    private static void addMapping(int port, int lease) throws Exception {
        soap("AddPortMapping", "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + port
                + "</NewExternalPort><NewProtocol>TCP</NewProtocol><NewInternalPort>" + port
                + "</NewInternalPort><NewInternalClient>" + localIp
                + "</NewInternalClient><NewEnabled>1</NewEnabled><NewPortMappingDescription>PlusCraft"
                + "</NewPortMappingDescription><NewLeaseDuration>" + lease + "</NewLeaseDuration>");
    }

    private static String soap(String action, String args) throws Exception {
        String body = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:" + action
                + " xmlns:u=\"" + serviceType + "\">" + args + "</u:" + action + "></s:Body></s:Envelope>";
        return http(URI.create(controlUrl).toURL(), "POST", serviceType + "#" + action, body);
    }

    private static String http(URL u, String method, String soapAction, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(4000);
        c.setRequestMethod(method);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            c.setRequestProperty("SOAPAction", "\"" + soapAction + "\"");
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        if (code >= 400) {
            Matcher m = Pattern.compile("<errorDescription>(.*?)</errorDescription>").matcher(text);
            throw new IOException("Router replied " + code + (m.find() ? " (" + m.group(1) + ")" : ""));
        }
        return text;
    }

    private static boolean isPrivate(String ip) {
        try {
            String[] p = ip.split("\\.");
            int a = Integer.parseInt(p[0]);
            int b = Integer.parseInt(p[1]);
            return a == 10 || (a == 192 && b == 168) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 100 && b >= 64 && b <= 127);
        } catch (Exception e) {
            return false;
        }
    }
}
