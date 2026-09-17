package com.urswolfer.intellij.plugin.gerrit;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import com.urswolfer.intellij.plugin.gerrit.ui.ShowProjectColumn;
import org.jdom.Attribute;
import org.jdom.Element;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;

public class GerritSettingsTest {

    private static final String PASSWORD_KEY = "GERRIT_SETTINGS_PASSWORD_KEY";

    /**
     * A settings file as every released version of the plugin has written it. The component element carries the
     * settings as attributes; the store strips the "name" attribute before handing the element to the component.
     */
    private static final String LEGACY_SETTINGS_XML =
        "<component Login=\"jdoe\" Host=\"https://gerrit.example.com\" ListAllChanges=\"true\""
            + " AutomaticRefresh=\"false\" RefreshTimeout=\"30\" ReviewNotifications=\"false\""
            + " PushToGerrit=\"true\" ShowChangeNumberColumn=\"true\" ShowChangeIdColumn=\"true\""
            + " ShowTopicColumn=\"true\" ShowProjectColumn=\"NEVER\""
            + " CloneBaseUrl=\"https://clone.example.com\" />";

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

    @Test
    public void testSettingsFileOfAnEarlierVersionIsLoaded() throws Exception {
        GerritSettings.SettingsState state = deserialize(LEGACY_SETTINGS_XML);

        Assert.assertEquals(state.login, "jdoe");
        Assert.assertEquals(state.host, "https://gerrit.example.com");
        Assert.assertTrue(state.listAllChanges);
        Assert.assertFalse(state.automaticRefresh);
        Assert.assertEquals(state.refreshTimeout, 30);
        Assert.assertFalse(state.reviewNotifications);
        Assert.assertTrue(state.pushToGerrit);
        Assert.assertTrue(state.showChangeNumberColumn);
        Assert.assertTrue(state.showChangeIdColumn);
        Assert.assertTrue(state.showTopicColumn);
        Assert.assertEquals(state.showProjectColumn, ShowProjectColumn.NEVER);
        Assert.assertEquals(state.cloneBaseUrl, "https://clone.example.com");
    }

    /**
     * The other direction of the same compatibility: what is written here has to be readable by a version which
     * still parses the attributes by hand.
     */
    @Test
    public void testSettingsAreWrittenUnderTheAttributeNamesOfEarlierVersions() throws Exception {
        Map<String, String> written = attributesOf(serialize(deserialize(LEGACY_SETTINGS_XML)));

        Assert.assertEquals(written, attributesOf(parse(LEGACY_SETTINGS_XML)));
    }

    /**
     * An earlier version reads a missing attribute as false or 0, not as the default which belongs to it, so the
     * defaults have to stay in the file even though the serializer would rather leave them out.
     */
    @Test
    public void testDefaultsAreWrittenSoEarlierVersionsDoNotReadThemAsFalse() throws Exception {
        Map<String, String> written = attributesOf(serialize(new GerritSettings.SettingsState()));

        Assert.assertEquals(written.keySet(), attributesOf(parse(LEGACY_SETTINGS_XML)).keySet());
        Assert.assertEquals(written.get("AutomaticRefresh"), "true");
        Assert.assertEquals(written.get("RefreshTimeout"), "15");
        Assert.assertEquals(written.get("ReviewNotifications"), "true");
        Assert.assertEquals(written.get("ShowProjectColumn"), "AUTO");
    }

    /**
     * {@link ShowProjectColumn#toString()} is a label for the settings combo box, so a serializer which wrote the
     * enum through it would put "Auto (when multiple Git repositories available)" into the file.
     */
    @Test
    public void testShowProjectColumnIsWrittenAsItsEnumName() throws Exception {
        GerritSettings.SettingsState state = new GerritSettings.SettingsState();
        state.showProjectColumn = ShowProjectColumn.ALWAYS;

        Assert.assertEquals(attributesOf(serialize(state)).get("ShowProjectColumn"), "ALWAYS");
    }

    /**
     * Parsing the attributes by hand mapped a missing AutomaticRefresh to false and a missing RefreshTimeout to 0,
     * which turned the automatic refresh off for a settings file which simply predates those attributes.
     */
    @Test
    public void testAttributesMissingFromTheFileKeepTheirDefault() throws Exception {
        GerritSettings.SettingsState state = deserialize("<component Host=\"https://gerrit.example.com\" />");

        Assert.assertEquals(state.host, "https://gerrit.example.com");
        Assert.assertTrue(state.automaticRefresh);
        Assert.assertEquals(state.refreshTimeout, 15);
        Assert.assertTrue(state.reviewNotifications);
        Assert.assertEquals(state.showProjectColumn, ShowProjectColumn.AUTO);
    }

    /**
     * A downgrade leaves the settings file of this version behind for an older build to read, so what is written
     * here has to survive the parser that build still uses. The reader below is that parser, copied verbatim from
     * the GerritSettings which preceded the state bean.
     */
    @Test
    public void testSettingsWrittenHereAreReadByTheParserOfEarlierVersions() throws Exception {
        GerritSettings.SettingsState state = deserialize(LEGACY_SETTINGS_XML);

        PreBeanSettingsReader read = new PreBeanSettingsReader(serialize(state));

        Assert.assertEquals(read.login, state.login);
        Assert.assertEquals(read.host, state.host);
        Assert.assertEquals(read.listAllChanges, state.listAllChanges);
        Assert.assertEquals(read.automaticRefresh, state.automaticRefresh);
        Assert.assertEquals(read.refreshTimeout, state.refreshTimeout);
        Assert.assertEquals(read.reviewNotifications, state.reviewNotifications);
        Assert.assertEquals(read.pushToGerrit, state.pushToGerrit);
        Assert.assertEquals(read.showChangeNumberColumn, state.showChangeNumberColumn);
        Assert.assertEquals(read.showChangeIdColumn, state.showChangeIdColumn);
        Assert.assertEquals(read.showTopicColumn, state.showTopicColumn);
        Assert.assertEquals(read.showProjectColumn, state.showProjectColumn);
        Assert.assertEquals(read.cloneBaseUrl, state.cloneBaseUrl);
    }

    /**
     * The defaults are the interesting half of a downgrade: the older parser maps an attribute which is not in the
     * file to false or 0, which would turn the automatic refresh off rather than leave it on.
     */
    @Test
    public void testDefaultsWrittenHereKeepTheirMeaningForEarlierVersions() {
        PreBeanSettingsReader read = new PreBeanSettingsReader(serialize(new GerritSettings.SettingsState()));

        Assert.assertTrue(read.automaticRefresh);
        Assert.assertEquals(read.refreshTimeout, 15);
        Assert.assertTrue(read.reviewNotifications);
        Assert.assertEquals(read.showProjectColumn, ShowProjectColumn.AUTO);
    }

    /**
     * Copied verbatim from GerritSettings#loadState as it read the file before the state bean replaced it.
     */
    private static final class PreBeanSettingsReader {
        private String login;
        private String host;
        private boolean listAllChanges;
        private boolean automaticRefresh;
        private int refreshTimeout;
        private boolean reviewNotifications;
        private boolean pushToGerrit;
        private boolean showChangeNumberColumn;
        private boolean showChangeIdColumn;
        private boolean showTopicColumn;
        private ShowProjectColumn showProjectColumn;
        private String cloneBaseUrl;

        PreBeanSettingsReader(Element element) {
            login = element.getAttributeValue("Login");
            host = element.getAttributeValue("Host");
            listAllChanges = getBooleanValue(element, "ListAllChanges");
            automaticRefresh = getBooleanValue(element, "AutomaticRefresh");
            refreshTimeout = getIntegerValue(element, "RefreshTimeout");
            reviewNotifications = getBooleanValue(element, "ReviewNotifications");
            pushToGerrit = getBooleanValue(element, "PushToGerrit");
            showChangeNumberColumn = getBooleanValue(element, "ShowChangeNumberColumn");
            showChangeIdColumn = getBooleanValue(element, "ShowChangeIdColumn");
            showTopicColumn = getBooleanValue(element, "ShowTopicColumn");
            showProjectColumn = getShowProjectColumnValue(element, "ShowProjectColumn");
            cloneBaseUrl = element.getAttributeValue("CloneBaseUrl");
        }

        private boolean getBooleanValue(Element element, String attributeName) {
            String attributeValue = element.getAttributeValue(attributeName);
            if (attributeValue != null) {
                return Boolean.valueOf(attributeValue);
            } else {
                return false;
            }
        }

        private int getIntegerValue(Element element, String attributeName) {
            String attributeValue = element.getAttributeValue(attributeName);
            if (attributeValue != null) {
                return Integer.valueOf(attributeValue);
            } else {
                return 0;
            }
        }

        private ShowProjectColumn getShowProjectColumnValue(Element element, String attributeName) {
            String attributeValue = element.getAttributeValue(attributeName);
            if (attributeValue != null) {
                return ShowProjectColumn.valueOf(attributeValue);
            } else {
                return ShowProjectColumn.AUTO;
            }
        }
    }

    private static GerritSettings.SettingsState deserialize(String xml) throws Exception {
        return XmlSerializer.deserialize(parse(xml), GerritSettings.SettingsState.class);
    }

    private static Element serialize(GerritSettings.SettingsState state) {
        return XmlSerializer.serialize(state, new SkipDefaultsSerializationFilter());
    }

    private static Element parse(String xml) throws Exception {
        return new org.jdom.input.SAXBuilder().build(new java.io.StringReader(xml)).getRootElement();
    }

    private static Map<String, String> attributesOf(Element element) {
        Map<String, String> attributes = new TreeMap<>();
        for (Attribute attribute : element.getAttributes()) {
            attributes.put(attribute.getName(), attribute.getValue());
        }
        return attributes;
    }

    private static CredentialAttributes credentialAttributes(String fieldName) throws Exception {
        Field field = GerritSettings.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (CredentialAttributes) field.get(null);
    }

    private static String serviceName(CredentialAttributes attributes) throws Exception {
        return attributes.getServiceName();
    }

    private static String userName(CredentialAttributes attributes) throws Exception {
        return attributes.getUserName();
    }
}
