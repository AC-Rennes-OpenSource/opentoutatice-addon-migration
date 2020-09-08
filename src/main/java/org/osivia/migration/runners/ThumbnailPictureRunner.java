/**
 * 
 */
package org.osivia.migration.runners;

import static org.nuxeo.ecm.core.api.CoreSession.ALLOW_VERSION_WRITE;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.IdUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.ListProperty;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.picture.api.ImagingService;
import org.nuxeo.ecm.platform.picture.api.adapters.PictureResourceAdapter;
import org.nuxeo.runtime.api.Framework;


/**
 * @author david
 *
 */
public class ThumbnailPictureRunner extends AbstractRunner {

    /** Logger. */
    private static final Log log = LogFactory.getLog(ThumbnailPictureRunner.class);

    /** Picture missing Thumbnail view query. */
    private static String PICT_MISSING_THUMB_VIEW_QUERY = "select * from Picture where ecm:isProxy = 0 and (file:content/data is not null or picture:views/1/content/data is not null) and (picture:views/4/content/data is null)";

    private static final String DOT = ".";
    private static final String WORD_SEPARATOR = "-";

    /** Picture xpath. */
    protected static final String FILE_CONTENT_XPATH = "file:content";
    protected static final String FILENAME_XPATH = "file:filename";
    protected static final String VIEWS_PROPERTY = "picture:views";
    protected static final String VIEW_XPATH_FILENAME = "picture:views/1/filename";
    protected static final String VIEW_XPATH_CONTENT = "picture:views/1/content";
    protected static final int NB_VIEWS = 5;

    /** Original view prefix. */
    private static final String ORIGINAL = "Original_";

    /** Imaging service. */
    private static ImagingService imagingService;
    /** Mime type service. */
    private static MimetypeRegistry mtRegistry;

    /** Getter for Imaging service. */
    protected static MimetypeRegistry getMimeTypeRegistry() {
        if (mtRegistry == null) {
            mtRegistry = Framework.getService(MimetypeRegistry.class);
        }
        return mtRegistry;
    }

    /** Getter for Imaging servcie. */
    protected static ImagingService getImagingService() {
        if (imagingService == null) {
            imagingService = Framework.getService(ImagingService.class);
        }
        return imagingService;
    }

    public ThumbnailPictureRunner(CoreSession session) {
        super(session);
    }

    /**
     * Pictures missing at least one view.
     */
    @Override
    public int setInputs(int limit) {
        int size = 0;

        this.inputs = this.session.query(PICT_MISSING_THUMB_VIEW_QUERY, limit);
        this.inputs = removeDocsWithError(this.inputs);

        size = this.inputs.size();

        this.totalInputs += size;
        return size;
    }

    /**
     * Recompute all views.
     */
    @Override
    public void run() throws ClientException {
        for (DocumentModel picture : this.inputs) {
            try {
                fillThumbnailView(picture);
            } catch (ClientException | IOException e) {
                // Store
                docsOnError.add(picture.getId());
                throw new ClientException(e);
            }
            // Persist
            this.session.save();
        }
    }

    /**
     * Launch work building thumbnail view.
     * 
     * @throws IOException
     * @throws ClientException
     */
    private Blob fillThumbnailView(DocumentModel picture) throws ClientException, IOException {

        PictureResourceAdapter pictureAdapter = picture.getAdapter(PictureResourceAdapter.class);
        String fileName = null;

        if (log.isTraceEnabled()) {
            if (picture != null) {
                String version = picture.isVersion() ? " -- V " + picture.getVersionLabel() : StringUtils.EMPTY;
                log.trace("Treating picture: " + picture.getPathAsString() + version);
            }
        }

        // Blob
        Property fileProp = picture.getProperty(FILE_CONTENT_XPATH);
        Blob blob = (Blob) fileProp.getValue();

        if (blob == null) {
            // No file:content -> get blob of Original view
            fileProp = picture.getProperty(VIEW_XPATH_CONTENT);
            blob = (Blob) fileProp.getValue();

            // Blob check
            if (blob.getLength() <= 0) {
                throw new ClientException(picture.getPathAsString() + " not treated: blob's length is 0");
            }

            fileName = getFileNameFromOrgView(blob, picture);
            blob.setFilename(fileName);

            // Restore file:content
            fileProp.setValue(blob);
            picture.setPropertyValue(FILE_CONTENT_XPATH, (Serializable) blob);
            picture.setPropertyValue(FILENAME_XPATH, fileName);
        } else {
            // Blob check
            if (blob.getLength() <= 0) {
                throw new ClientException(picture.getPathAsString() + " not treated: blob's length is 0");
            }

            // File name computing from file
            fileName = getFileNameFromContent(picture, blob);
            // Restore file if name or filename null
            if (!StringUtils.equals(blob.getFilename(), fileName)) {
                blob.setFilename(fileName);
                picture.setPropertyValue(FILENAME_XPATH, fileName);
            }
        }


        // Re-generate all views
        boolean done = pictureAdapter.fillPictureViews(blob, fileName, picture.getTitle(), null);
        if (done) {
            checkTreatment(pictureAdapter, picture);
        } else {
            throw new ClientException(picture.getPathAsString() + " not treated: unknown error");
        }

        if (picture.isVersion()) {
            picture.putContextData(ALLOW_VERSION_WRITE, Boolean.TRUE);
        }
        this.session.saveDocument(picture);

        this.treatedInputs += 1;

        return blob;
    }

    /**
     * @param picture
     * @param blob
     * @return
     */
    protected String getFileNameFromContent(DocumentModel picture, Blob blob) {
        String fileName = blob.getFilename();

        if (StringUtils.isBlank(fileName)) {
            fileName = (String) picture.getPropertyValue(FILENAME_XPATH);
            if (StringUtils.isBlank(fileName)) {
                fileName = generateFileName(picture, blob);
            }
        }
        return fileName;
    }

    /**
     * @param picture
     * @param blob
     * @param fileName
     * @return
     */
    protected String generateFileName(DocumentModel picture, Blob blob) {
        String fileName = picture.getTitle();

        // Title contains yet an extension
        if (StringUtils.contains(fileName, DOT)) {
            String[] parts = StringUtils.split(fileName, DOT);
            fileName = IdUtils.generateId(parts[0], WORD_SEPARATOR, true, 30) + DOT + IdUtils.generateId(parts[1], WORD_SEPARATOR, true, 4);
        } else {
            List<String> extensions = getMimeTypeRegistry().getExtensionsFromMimetypeName(blob.getMimeType());
            if (extensions.size() > 0) {
                fileName = IdUtils.generateId(picture.getTitle(), WORD_SEPARATOR, true, 30) + DOT + extensions.get(0);
            } else {
                throw new ClientException(picture.getPathAsString() + " not treated: mime-type not found");
            }
        }

        return fileName;
    }

    /**
     * Checks if treatment is ok.
     * 
     * @param pictureAdapter
     * @param picture
     */
    private void checkTreatment(PictureResourceAdapter pictureAdapter, DocumentModel picture) throws ClientException {
        ListProperty views = (ListProperty) picture.getProperty(VIEWS_PROPERTY);
        if (views.size() < 5) {
            throw new ClientException(picture.getPathAsString() + " not treated");
        }
    }

    /**
     * Gets file's name.
     * 
     * @param picture
     * @return file name
     */
    protected String getFileNameFromOrgView(Blob blob, DocumentModel picture) throws ClientException {
        // Name of original view
        String fileName = blob.getFilename();

        if (StringUtils.isNotBlank(fileName)) {
            fileName = StringUtils.startsWith(fileName, ORIGINAL) ? StringUtils.substringAfter(fileName, ORIGINAL) : fileName;
        } else {
            fileName = (String) picture.getPropertyValue(VIEW_XPATH_FILENAME);
            fileName = StringUtils.startsWith(fileName, ORIGINAL) ? StringUtils.substringAfter(fileName, ORIGINAL) : fileName;

            if (StringUtils.isBlank(fileName)) {
                fileName = generateFileName(picture, blob);
            }
        }

        return fileName;
    }

}
