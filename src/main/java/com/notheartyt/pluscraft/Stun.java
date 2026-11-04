package com.notheartyt.pluscraft;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.SecureRandom;

/** Minimal STUN (RFC 5389) binding request to discover this machine's public IPv4 address. */
public final class Stun {
    public static volatile String publicIp = null;
    public static volatile String error = null;
    public static volatile boolean busy = false;

    private Stun() {}

    public static void lookupAsync() {
        if (busy) return;
        busy = true;
        error = null;
        Thread t = new Thread(() -> {
            try {
                publicIp = query();
            } catch (Exception e) {
                error = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            } finally {
                busy = false;
            }
        }, "PlusCraft-STUN");
        t.setDaemon(true);
        t.start();
    }

    private static String query() throws Exception {
        byte[] req = new byte[20];
        req[1] = 0x01; // Binding Request
        req[4] = 0x21;
        req[5] = 0x12;
        req[6] = (byte) 0xA4;
        req[7] = 0x42; // magic cookie
        byte[] tx = new byte[12];
        new SecureRandom().nextBytes(tx);
        System.arraycopy(tx, 0, req, 8, 12);

        try (DatagramSocket s = new DatagramSocket()) {
            s.setSoTimeout(3000);
            InetAddress server = InetAddress.getByName("stun.l.google.com");
            s.send(new DatagramPacket(req, req.length, server, 19302));
            byte[] buf = new byte[512];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            s.receive(p);

            int total = Math.min(p.getLength(), 20 + (((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF)));
            int off = 20;
            while (off + 4 <= total) {
                int type = ((buf[off] & 0xFF) << 8) | (buf[off + 1] & 0xFF);
                int len = ((buf[off + 2] & 0xFF) << 8) | (buf[off + 3] & 0xFF);
                int b = off + 4;
                if (len >= 8 && b + 8 <= total && buf[b + 1] == 0x01) {
                    if (type == 0x0020) { // XOR-MAPPED-ADDRESS
                        return ((buf[b + 4] & 0xFF) ^ 0x21) + "." + ((buf[b + 5] & 0xFF) ^ 0x12) + "."
                                + ((buf[b + 6] & 0xFF) ^ 0xA4) + "." + ((buf[b + 7] & 0xFF) ^ 0x42);
                    }
                    if (type == 0x0001) { // MAPPED-ADDRESS (old servers)
                        return (buf[b + 4] & 0xFF) + "." + (buf[b + 5] & 0xFF) + "."
                                + (buf[b + 6] & 0xFF) + "." + (buf[b + 7] & 0xFF);
                    }
                }
                off += 4 + ((len + 3) & ~3);
            }
            throw new IOException("No address in STUN reply");
        }
    }
}
