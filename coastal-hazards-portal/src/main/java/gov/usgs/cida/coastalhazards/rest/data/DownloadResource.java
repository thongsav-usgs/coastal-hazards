package gov.usgs.cida.coastalhazards.rest.data;

import gov.usgs.cida.coastalhazards.download.DownloadUtility;
import gov.usgs.cida.coastalhazards.jpa.DownloadManager;
import gov.usgs.cida.coastalhazards.jpa.ItemManager;
import gov.usgs.cida.coastalhazards.model.Item;
import gov.usgs.cida.coastalhazards.jpa.SessionManager;
import gov.usgs.cida.coastalhazards.model.Session;
import gov.usgs.cida.coastalhazards.model.util.Download;
import gov.usgs.cida.coastalhazards.session.io.SessionIOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
@Path("download")
public class DownloadResource {

    private static final SessionManager sessionManager = new SessionManager();

    /**
     * Retrieves representation of an instance of
     * gov.usgs.cida.coastalhazards.model.Item
     *
     * @param id identifier of requested item
     * @return JSON representation of the item(s)
     * @throws java.io.IOException
     */
    @GET
    @Path("item/{id}")
    @Produces("application/zip")
    public Response getCard(@PathParam("id") String id) throws IOException {
        Response response = null;
        try (ItemManager itemManager = new ItemManager()) {
            Item item = itemManager.load(id);
            if (item == null) {
                response = Response.status(Response.Status.NOT_FOUND).build();
            } else {
                File zipFile = null;
                DownloadManager manager = new DownloadManager();
                try {
                    if (manager.isPersisted(id)) {
                        Download persistedDownload = manager.load(id);
                        // if we switch this to external file server or S3,
                        // redirect to this uri as a url
                        zipFile = new File(new URI(persistedDownload.getPersistanceURI()));
                    } else {
                        throw new FileNotFoundException();
                    }
                } catch (FileNotFoundException | URISyntaxException ex) {
                    File stagingDir = DownloadUtility.createDownloadStagingArea();
                    DownloadUtility.stageItemDownload(item, stagingDir);
                    zipFile = DownloadUtility.zipStagingAreaForDownload(stagingDir);
                } finally {
                    Download download = new Download();
                    download.setItemId(id);
                    download.setPersistanceURI(zipFile.toURI().toString());
                    manager.save(download);
                }
                response = Response.ok(zipFile, "application/zip").build();
            }
        }
        return response;
    }

    /**
     * Retrieves representation of an instance of
     * gov.usgs.cida.coastalhazards.model.Item
     *
     * @param id identifier of requested item
     * @return JSON representation of the item(s)
     * @throws java.io.IOException
     */
    @GET
    @Path("view/{id}")
    @Produces("application/zip")
    public Response getSession(@PathParam("id") String id) throws IOException {
        Response response = null;
        try {
            String sessionJSON = sessionManager.load(id);
            if (sessionJSON == null) {
                response = Response.status(Response.Status.NOT_FOUND).build();
            } else {
                DownloadManager manager = new DownloadManager();
                File zipFile = null;
                try {
                    if (manager.isPersisted(id)) {
                        Download download = manager.load(id);
                        zipFile = new File(new URI(download.getPersistanceURI()));
                    } else {
                        throw new FileNotFoundException();
                    }
                } catch (FileNotFoundException| URISyntaxException ex) {
                    Session session = Session.fromJSON(sessionJSON);
                    File stagingDir = DownloadUtility.createDownloadStagingArea();
                    DownloadUtility.stageSessionDownload(session, stagingDir);
                    zipFile = DownloadUtility.zipStagingAreaForDownload(stagingDir);
                } finally {
                    Download download = new Download();
                    download.setSessionId(id);
                    download.setPersistanceURI(zipFile.toURI().toString());
                    manager.save(download);
                }
                response = Response.ok(zipFile, "application/zip").build();
            }
        } catch (NoSuchAlgorithmException | SessionIOException ex) {
            response = Response.serverError().entity(ex).build();
        }
        return response;
    }
}
