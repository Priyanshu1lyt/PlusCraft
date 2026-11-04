package com.notheartyt.pluscraft;

/** Short shareable join codes: IPv4 + port packed into 48 bits, written as 10 base32 characters. */
public final class JoinCode {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private JoinCode() {}

    public static String encode(String ip, int port) {
        String[] p = ip.split("\\.");
        if (p.length != 4) throw new IllegalArgumentException("IPv4 only");
        long v = 0;
        for (String part : p) v = (v << 8) | (Integer.parseInt(part) & 0xFF);
        v = (v << 16) | (port & 0xFFFF);
        v <<= 2; // 48 bits -> 50 bits = 10 characters
        StringBuilder sb = new StringBuilder();
        for (int i = 9; i >= 0; i--) sb.append(ALPHABET.charAt((int) ((v >> (5 * i)) & 31)));
        return "PLUS-" + sb.substring(0, 5) + "-" + sb.substring(5);
    }

    /** Returns {ip, port} or null if the code is not valid. */
    public static String[] decode(String code) {
        if (code == null) return null;
        StringBuilder clean = new StringBuilder();
        for (char c : code.toUpperCase().toCharArray()) {
            if (ALPHABET.indexOf(c) >= 0) clean.append(c);
        }
        String s = clean.toString();
        if (s.length() == 14 && s.startsWith("PLUS")) s = s.substring(4);
        if (s.length() != 10) return null;
        long v = 0;
        for (int i = 0; i < 10; i++) v = (v << 5) | ALPHABET.indexOf(s.charAt(i));
        v >>= 2;
        int port = (int) (v & 0xFFFF);
        long ipv = v >> 16;
        if (port < 1024) return null;
        String ip = ((ipv >> 24) & 0xFF) + "." + ((ipv >> 16) & 0xFF) + "." + ((ipv >> 8) & 0xFF) + "." + (ipv & 0xFF);
        return new String[]{ip, String.valueOf(port)};
    }
}
