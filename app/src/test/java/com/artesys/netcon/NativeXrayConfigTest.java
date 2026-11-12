package com.artesys.netcon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

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
        org.junit.Assert.assertFalse(json.contains("geoip:private"));
        String firstIp = imported.config
            .getJSONObject("routing")
            .getJSONArray("rules")
            .getJSONObject(0)
            .getJSONArray("ip")
            .getString(0);
        assertEquals("10.0.0.0/8", firstIp);
        assertEquals("direct", imported.config.getJSONArray("outbounds").getJSONObject(1).getString("tag"));
    }
}
