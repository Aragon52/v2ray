package com.artesys.netcon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class NativeXrayConfigTest {
    @Test
    public void acceptsLocalXrayJson() throws Exception {
        NativeXrayConfig.ImportedServer imported = NativeXrayConfig.fromInput("{\"outbounds\":[{\"protocol\":\"freedom\"}]}");
        assertEquals("Imported Xray configuration", imported.name);
        assertEquals(1, imported.config.getJSONArray("outbounds").length());
    }

    @Test
    public void rejectsEmptyConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> NativeXrayConfig.fromInput(" "));
    }

    @Test
    public void rejectsJsonWithoutProxyOutbound() {
        assertThrows(Exception.class, () -> NativeXrayConfig.fromInput("{\"inbounds\":[]}"));
    }

    @Test
    public void rejectsJsonWithEmptyOutbounds() {
        assertThrows(IllegalArgumentException.class, () -> NativeXrayConfig.fromInput("{\"outbounds\":[]}"));
    }

    @Test
    public void parsesVlessWithoutGeoipFileDependency() throws Exception {
        NativeXrayConfig.ImportedServer imported = NativeXrayConfig.fromInput(
            "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=ws&path=/ws#EdgeNode"
        );
        assertEquals("EdgeNode", imported.name);
        String json = imported.config.toString();
        assertFalse(json.contains("geoip:private"));
        String firstIp = imported.config
            .getJSONObject("routing")
            .getJSONArray("rules")
            .getJSONObject(0)
            .getJSONArray("ip")
            .getString(0);
        assertEquals("10.0.0.0/8", firstIp);
        assertEquals("direct", imported.config.getJSONArray("outbounds").getJSONObject(1).getString("tag"));
    }

    @Test
    public void parsesTrojanGrpcAndSocks5ProfilesCleanly() throws Exception {
        NativeXrayConfig.ImportedServer trojan = NativeXrayConfig.fromInput(
            "trojan://secretpass@us-edge.example.com:443?type=grpc&serviceName=gun-tun&security=tls#US-Trojan"
        );
        assertEquals("US-Trojan", trojan.name);
        assertEquals("trojan", trojan.config.getJSONArray("outbounds").getJSONObject(0).getString("protocol"));

        NativeXrayConfig.ImportedServer socks = NativeXrayConfig.fromInput(
            "socks5://user:pass@198.51.100.10:1080#GB-SocksRelay"
        );
        assertEquals("GB-SocksRelay", socks.name);
        assertEquals("socks", socks.config.getJSONArray("outbounds").getJSONObject(0).getString("protocol"));
    }

    @Test
    public void providesStabilizedMultiProtocolPresetProfiles() {
        List<NativeXrayConfig.ServerProfilePreset> presets = NativeXrayConfig.presetProfiles();
        assertTrue(presets.size() >= 6);
        assertEquals("SG-Cloudflare-Reality-01", presets.get(0).name);
        assertEquals("SG", presets.get(0).countryCode);
    }
}
