package net.andylizi.haproxydetector;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ProxyWhitelist {
    @Nullable
    public static ProxyWhitelist whitelist = new ProxyWhitelist(new ArrayList<>(0));

    private static volatile InetAddress lastWarning;

    public static boolean check(SocketAddress addr) {
        if (whitelist == null) return true;
        return addr instanceof InetSocketAddress && whitelist.matches(((InetSocketAddress) addr).getAddress());
    }

    public static Optional<String> getWarningFor(SocketAddress socketAddress) {
        if (!(socketAddress instanceof InetSocketAddress)) return Optional.empty();
        InetAddress address = ((InetSocketAddress) socketAddress).getAddress();
        if (!address.equals(lastWarning)) {
            lastWarning = address;
            return Optional.of("代理连接来源地址 " + address + " 不在白名单中");
        }
        return Optional.empty();
    }

    public static Optional<ProxyWhitelist> loadOrDefault(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path) || Files.isDirectory(path)) {
            Files.write(path, Arrays.asList(
                "# 允许的代理 IP 列表",
                "#",
                "# 空白名单将拒绝所有代理。",
                "# 每一行必须是有效的 IP 地址、域名或 CIDR。",
                "# 域名仅在启动时解析一次。",
                "# 单个域名可解析出多个 A/AAAA 记录，均会被允许。",
                "# 域名不支持附带 CIDR 前缀。",
                "",
                "127.0.0.0/8",
                "::1/128"
            ), StandardCharsets.UTF_8);
        }
        return load(path);
    }

    public static Optional<ProxyWhitelist> load(Path path) throws IOException {
        ArrayList<CIDR> list = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            boolean first = true;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) continue;

                if (first && line.startsWith("YesIReallyWantToDisableWhitelistItsExtremelyDangerousButIKnowWhatIAmDoing")) {
                    return Optional.empty();
                }
                first =false;
                list.addAll(CIDR.parse(line));
            }
        }
        return Optional.of(new ProxyWhitelist(list));
    }

    private final List<CIDR> list;

    private ProxyWhitelist(ArrayList<CIDR> list) {
        this.list = list;
    }

    public ProxyWhitelist(List<CIDR> list) {
        this.list = new ArrayList<>(list);
    }

    public boolean matches(InetAddress addr) {
        for (CIDR ip : list) {
            if (ip.contains(addr)) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return this.list.size();
    }

    @Override
    public String toString() {
        return "ProxyWhitelist" + list;
    }
}
