package org.ops4j.pax.url.mvn.s3;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.EnvironmentStringPBEConfig;
import org.jasypt.properties.PropertyValueEncryptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialsDecryptor {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialsDecryptor.class);

    private static final String PASSWORD_ENV_VAR = "TMC_ENGINE_PASSWORD";

    private StringEncryptor encryptor;

    public CredentialsDecryptor() {
        if (null == System.getenv(PASSWORD_ENV_VAR)) {
            LOG.debug("sensitive engine configuration encryption is not enabled (no password specified)");
            return;
        }
        try {
            EnvironmentStringPBEConfig env = new EnvironmentStringPBEConfig();
            env.setProvider(new BouncyCastleProvider());
            env.setAlgorithm("PBEWITHSHA256AND256BITAES-CBC-BC");
            env.setPasswordEnvName(PASSWORD_ENV_VAR);

            StandardPBEStringEncryptor pbeStringEncryptor = new StandardPBEStringEncryptor();
            pbeStringEncryptor.setConfig(env);
            encryptor = pbeStringEncryptor;
        } catch (Exception e) {
            LOG.error("cannot initialize sensitive engine configuration decryptor", e);
        }
    }

    public String decrypt(String name, String value) {
        if (null != encryptor) {
            try {
                return PropertyValueEncryptionUtils.decrypt(value, encryptor);
            } catch (Exception e) {
                LOG.error("cannot decrypt sensitive engine configuration value for {}", name);
            }
        }
        return value;
    }

}
