package us.bringardner.parley.files.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import us.bringardner.parley.files.ConnectionSetting;

/** The JavaFX form drawn from connection settings; it behaves like parley-files-swing's ConnectionSettingsPanel. */
public class ConnectionSettingsFormTest {

	static final List<ConnectionSetting> SETTINGS = List.of(
			ConnectionSetting.text("host", "Host").asRequired(),
			ConnectionSetting.integer("port", "Port", 1L, 65535L).withDefault("22"),
			ConnectionSetting.choice("auth", "Authentication", "password", "keyFile", "key"),
			ConnectionSetting.secret("password", "Password").visibleWhen("auth", "password"),
			ConnectionSetting.localFile("identityFile", "Key file").visibleWhen("auth", "keyFile"),
			ConnectionSetting.multilineSecret("privateKey", "Key").visibleWhen("auth", "key"),
			ConnectionSetting.bool("secure", "Secure"),
			ConnectionSetting.integer("timeout", "Timeout", 0L, null).asAdvanced(),
			ConnectionSetting.hidden("sessionKey"));

	private static Properties props(String... kv) {
		Properties p = new Properties();
		for(int i=0; i < kv.length; i += 2) {
			p.setProperty(kv[i], kv[i+1]);
		}
		return p;
	}

	@Test
	public void aFieldOfTheRightKindForEachSetting() throws Exception {
		Fx.run(()->{
			ConnectionSettingsForm form = new ConnectionSettingsForm();
			form.setSettings(SETTINGS, props("host", "example.com", "password", "pw", "sessionKey", "abc"));
			assertInstanceOf(PasswordField.class, form.fieldFor("password"));
			assertInstanceOf(ComboBox.class, form.fieldFor("auth"));
			assertInstanceOf(TextArea.class, form.fieldFor("privateKey"));
			assertInstanceOf(CheckBox.class, form.fieldFor("secure"));
			assertEquals("22", ((TextField) form.fieldFor("port")).getText());
			assertNull(form.fieldFor("sessionKey"));

			Properties got = form.getConnectionProperties();
			assertEquals("example.com", got.getProperty("host"));
			assertEquals("pw", got.getProperty("password"));
			assertEquals("false", got.getProperty("secure"));
			// hidden settings are carried through
			assertEquals("abc", got.getProperty("sessionKey"));
		});
	}

	@Test
	public void choosingShowsAndHidesSettings() throws Exception {
		Fx.run(()->{
			ConnectionSettingsForm form = new ConnectionSettingsForm();
			form.setSettings(SETTINGS, props("password", "pw"));
			assertTrue(form.isShown("password"));
			assertFalse(form.isShown("identityFile"));
			assertFalse(form.isShown("privateKey"));
			// advanced settings stay out of the way until asked for
			assertFalse(form.isShown("timeout"));
			form.setShowAdvanced(true);
			assertTrue(form.isShown("timeout"));

			@SuppressWarnings("unchecked")
			ComboBox<String> auth = (ComboBox<String>) form.fieldFor("auth");
			auth.setValue("keyFile");
			assertFalse(form.isShown("password"));
			assertTrue(form.isShown("identityFile"));
			((TextField) form.fieldFor("identityFile")).setText("/home/me/.ssh/id");
			((TextField) form.fieldFor("host")).setText(" h ");

			Properties got = form.getConnectionProperties();
			// the password no longer applies, so it isn't sent
			assertEquals("", got.getProperty("password"));
			assertEquals("/home/me/.ssh/id", got.getProperty("identityFile"));
			assertEquals("h", got.getProperty("host"));
			assertEquals(List.of(), form.validateValues());
		});
	}

	@Test
	public void problemsAreReported() throws Exception {
		Fx.run(()->{
			ConnectionSettingsForm form = new ConnectionSettingsForm();
			form.setSettings(SETTINGS, new Properties());
			((TextField) form.fieldFor("port")).setText("abc");
			assertEquals(List.of("Host is required", "Port must be a whole number"), form.validateValues());
		});
	}

	@Test
	public void secretsKeepTheirSpaces() throws Exception {
		Fx.run(()->{
			ConnectionSettingsForm form = new ConnectionSettingsForm();
			form.setSettings(SETTINGS, props("host", "h", "password", " pass word "));
			assertEquals(" pass word ", form.getConnectionProperties().getProperty("password"));
		});
	}

	@Test
	public void noSettings() throws Exception {
		Fx.run(()->{
			ConnectionSettingsForm form = new ConnectionSettingsForm();
			form.setSettings(List.of(), props("other", "kept"));
			assertEquals(List.of(), form.validateValues());
			assertEquals("kept", form.getConnectionProperties().getProperty("other"));
		});
	}

	/** A keychain in memory that can list what it holds. */
	static class FakeKeychain implements us.bringardner.parley.files.credentials.CredentialProvider {
		final java.util.Map<String, String> entries = new java.util.TreeMap<>();
		@Override public String scheme() { return "keychain"; }
		@Override public boolean isAvailable() { return true; }
		@Override public boolean canStore() { return true; }
		@Override public char[] resolve(us.bringardner.parley.files.credentials.CredentialRef ref,
				us.bringardner.parley.files.credentials.CredentialRequest req) {
			return entries.get(ref.locator()).toCharArray();
		}
		@Override public void store(us.bringardner.parley.files.credentials.CredentialRef ref, char[] secret) {
			entries.put(ref.locator(), new String(secret));
		}
		@Override public List<us.bringardner.parley.files.credentials.CredentialRef> list() {
			List<us.bringardner.parley.files.credentials.CredentialRef> ret = new java.util.ArrayList<>();
			for(String k : entries.keySet()) {
				ret.add(new us.bringardner.parley.files.credentials.CredentialRef("keychain", k));
			}
			return ret;
		}
	}

	private static final List<ConnectionSetting> WITH_USER = List.of(
			ConnectionSetting.text("host", "Host"), ConnectionSetting.text("user", "User"),
			ConnectionSetting.secret("password", "Password"));

	@Test
	public void aReferenceIsShownInClearAndAPasswordMasked() throws Exception {
		Fx.run(()->{
			ConnectionSettingsForm form = new ConnectionSettingsForm();
			form.setSettings(WITH_USER, props("host", "prod", "user", "deploy", "password", "@env:DB_PW"));
			PasswordField pw = (PasswordField) form.fieldFor("password");
			assertFalse(pw.isVisible(), "a reference isn't a secret: the clear field shows it");
			pw.setText("hunter2");
			assertTrue(pw.isVisible(), "a password is masked");
			assertEquals("hunter2", form.getConnectionProperties().getProperty("password"));
		});
	}

	@Test
	public void aTypedPasswordCanBeSavedToTheKeychain() throws Exception {
		FakeKeychain keychain = new FakeKeychain();
		us.bringardner.parley.files.credentials.Credentials.register(keychain);
		try {
			Fx.run(()->{
				ConnectionSettingsForm form = new ConnectionSettingsForm();
				form.setFactoryId("sftp");
				form.setSettings(WITH_USER, props("host", "prod", "user", "deploy", "password", "hunter2"));
				CheckBox save = form.saveToKeychainFor("password");
				assertTrue(save != null && !save.isDisabled());
				save.setSelected(true);
				Properties p = form.getConnectionProperties();
				assertEquals("@keychain:parley/sftp/deploy@prod", p.getProperty("password"));
				assertEquals("hunter2", keychain.entries.get("parley/sftp/deploy@prod"));
				assertFalse(save.isSelected());
				assertTrue(save.isDisabled(), "nothing to save once the field holds a reference");
				assertEquals(List.of("@keychain:parley/sftp/deploy@prod"), ConnectionSettingsForm.storedReferences());
			});
		} finally {
			us.bringardner.parley.files.credentials.Credentials.restoreDefaults();
		}
	}

	@Test
	public void anUnknownStoreIsReported() throws Exception {
		Fx.run(()->{
			ConnectionSettingsForm form = new ConnectionSettingsForm();
			form.setSettings(WITH_USER, props("password", "@vault:prod"));
			assertEquals(List.of("Password: there is no credential store called 'vault' (@vault:prod)"), form.validateValues());
		});
	}
}
