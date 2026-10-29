package org.acme.documents;

import java.io.IOException;
import java.sql.SQLException;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Documents uploaded by users. The upload lands in a temporary file that Quarkus deletes when the request ends,
 * so the resource has to move it somewhere permanent. Where is the whole question: the local disk of the
 * instance that happened to receive the request, or storage every instance can reach.
 */
@jakarta.ws.rs.Path("/documents")
public class DocumentResource {

    private final DocumentService documents;

    public DocumentResource(DocumentService documents) {
        this.documents = documents;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    public String upload(@RestForm("file") FileUpload file) throws IOException, SQLException {
        return documents.store(file.uploadedFile());
    }

    @GET
    @jakarta.ws.rs.Path("/{id}")
    public Response download(@PathParam("id") String id) throws IOException, SQLException {
        byte[] content = documents.read(id);
        return content == null
                ? Response.status(Response.Status.NOT_FOUND).build()
                : Response.ok(content, MediaType.APPLICATION_OCTET_STREAM).build();
    }
}
