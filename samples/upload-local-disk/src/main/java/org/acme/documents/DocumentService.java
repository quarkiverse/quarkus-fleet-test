package org.acme.documents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class DocumentService {

    private final AgroalDataSource dataSource;

    // The fix: "database" instead of "disk".
    private final String store;

    public DocumentService(AgroalDataSource dataSource,
            @ConfigProperty(name = "documents.store", defaultValue = "disk") String store) {
        this.dataSource = dataSource;
        this.store = store;
    }

    public String store(Path uploadedFile) throws IOException, SQLException {
        String id = UUID.randomUUID().toString();
        if (store.equals("database")) {
            storeInDatabase(id, uploadedFile);
        } else {
            storeOnDisk(id, uploadedFile);
        }
        return id;
    }

    public byte[] read(String id) throws IOException, SQLException {
        return store.equals("database") ? readFromDatabase(id) : readFromDisk(id);
    }

    // Each instance has a temp directory of its own, so this copy exists for this instance only.
    private void storeOnDisk(String id, Path upload) throws IOException {
        Path documents = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "documents"));
        Files.copy(upload, documents.resolve(id));
    }

    private byte[] readFromDisk(String id) throws IOException {
        Path document = Path.of(System.getProperty("java.io.tmpdir"), "documents", id);
        return Files.exists(document) ? Files.readAllBytes(document) : null;
    }

    private void storeInDatabase(String id, Path upload) throws IOException, SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement insert = c.prepareStatement("INSERT INTO documents (id, content) VALUES (?, ?)")) {
            insert.setString(1, id);
            insert.setBytes(2, Files.readAllBytes(upload));
            insert.executeUpdate();
        }
    }

    private byte[] readFromDatabase(String id) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement select = c.prepareStatement("SELECT content FROM documents WHERE id = ?")) {
            select.setString(1, id);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }
}
