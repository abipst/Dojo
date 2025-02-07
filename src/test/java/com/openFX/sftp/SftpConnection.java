package com.openFX.sftp;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import com.openFX.utils.LoggerUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.NoSuchAlgorithmException;

public class SftpConnection {
    private static final Logger logger = LoggerUtil.getLogger(SftpConnection.class);

    private SshClient client;
    private SftpClient sftpClient;

    // Load environment variables from .env file
    private static final Dotenv dotenv = Dotenv.load();

    private static final String REMOTE_HOSTNAME = dotenv.get("REMOTE_HOSTNAME");
    private static final int REMOTE_PORT = Integer.parseInt(dotenv.get("REMOTE_PORT", "22")); // default to 22
    private static final String REMOTE_USERNAME = dotenv.get("REMOTE_USERNAME");
    private static final String PRIVATE_KEY_FILE_PATH = dotenv.get("PRIVATE_KEY_FILE_PATH");

    public SftpConnection() {
        this.client = SshClient.setUpDefaultClient(); // Set up SSH client
    }

    // Establish connection to remote SFTP server
    public void connect() throws IOException, NoSuchAlgorithmException {
        // Start SSH client
        client.start();

        // Create SSH session
        var session = client.connect(REMOTE_USERNAME, REMOTE_HOSTNAME, REMOTE_PORT).verify().getSession();

        // Load and add the private key identity
        String privateKeyPath = dotenv.get("PRIVATE_KEY_FILE_PATH");
        File privateKeyFile = new File(privateKeyPath);

        if (privateKeyFile.exists()) {
            // Load the private key
            PrivateKey privateKey = loadPrivateKey(privateKeyFile);

            // Create KeyPair from private key
            KeyPair keyPair = new KeyPair(getPublicKeyFromPrivate(privateKey), privateKey);

            // Add public key identity for SSH authentication
            session.addPublicKeyIdentity(keyPair);
        }

        // Authenticate the session
        session.auth().verify();

        // Create SFTP session using the authenticated session
        SftpClientFactory factory = SftpClientFactory.instance();
        sftpClient = factory.createSftpClient(session);

        logger.info("SFTP connection established to {}", REMOTE_HOSTNAME);
    }

    // Get the SFTP client
    public SftpClient getSftpClient() {
        return sftpClient;
    }

    // Disconnect the client and close the session
    public void disconnect() {
        try {
            if (sftpClient != null) {
                sftpClient.close();
                logger.info("SFTP client connection closed.");
            }
            if (client != null) {
                client.stop();
                logger.info("SSH client connection stopped.");
            }
        } catch (IOException e) {
            logger.error("Error closing SFTP connection", e);
        }
    }

    // Helper method to load a private key from a file
    private PrivateKey loadPrivateKey(File privateKeyFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(privateKeyFile)) {
            byte[] keyBytes = fis.readAllBytes();
            // Use KeyFactory to load the private key from the byte array
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
            return privateKey;
        } catch (Exception e) {
            throw new IOException("Failed to load private key", e);
        }
    }

    // Helper method to extract the public key from the private key
    private PublicKey getPublicKeyFromPrivate(PrivateKey privateKey) throws IOException, NoSuchAlgorithmException {
        if (privateKey instanceof RSAPrivateKey) {
            RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) privateKey;

            // Create RSAPublicKeySpec from private key's modulus and public exponent
            RSAPublicKeySpec pubKeySpec = new RSAPublicKeySpec(
                    rsaPrivateKey.getModulus(),
                    rsaPrivateKey.getPrivateExponent() // Using the private exponent directly (this is where you get the
                                                       // modulus)
            );

            // Use KeyFactory to generate the public key from the spec
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            try {
                RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(pubKeySpec);
                return publicKey;
            } catch (InvalidKeySpecException e) {
                throw new IOException("Failed to generate public key from private key", e);
            }
        }
        throw new IOException("Only RSA private keys are supported in this example.");
    }

    // Additional helper methods to retrieve the connection parameters (optional)
    public static String getRemoteHostname() {
        return REMOTE_HOSTNAME;
    }

    public static int getRemotePort() {
        return REMOTE_PORT;
    }

    public static String getRemoteUsername() {
        return REMOTE_USERNAME;
    }

    public static String getPrivateKeyFilePath() {
        return PRIVATE_KEY_FILE_PATH;
    }
}
