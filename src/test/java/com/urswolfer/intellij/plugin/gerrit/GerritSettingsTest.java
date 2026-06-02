package com.urswolfer.intellij.plugin.gerrit;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GerritSettingsTest {

    private static final String PASSWORD_KEY = "GERRIT_SETTINGS_PASSWORD_KEY";

    @Test
    public void testCredentialAttributesUseIntelliJPlatformServiceName() throws Exception {
        CredentialAttributes attributes = credentialAttributes("CREDENTIAL_ATTRIBUTES");

        Assert.assertEquals(serviceName(attributes), CredentialAttributesKt.generateServiceName("Gerrit", PASSWORD_KEY));
        Assert.assertEquals(userName(attributes), PASSWORD_KEY);
    }

    @Test
    public void testLegacyCredentialAttributesAreKeptForMigration() throws Exception {
        CredentialAttributes attributes = credentialAttributes("LEGACY_CREDENTIAL_ATTRIBUTES");

        Assert.assertEquals(serviceName(attributes), GerritSettings.class.getName());
        Assert.assertEquals(userName(attributes), PASSWORD_KEY);
    }

    private static CredentialAttributes credentialAttributes(String fieldName) throws Exception {
        Field field = GerritSettings.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (CredentialAttributes) field.get(null);
    }

    private static String serviceName(CredentialAttributes attributes) throws Exception {
        return (String) credentialAttributesMethod("getServiceName").invoke(attributes);
    }

    private static String userName(CredentialAttributes attributes) throws Exception {
        return (String) credentialAttributesMethod("getUserName").invoke(attributes);
    }

    private static Method credentialAttributesMethod(String name) throws Exception {
        return CredentialAttributes.class.getMethod(name);
    }
}
