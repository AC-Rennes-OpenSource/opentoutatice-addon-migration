/**
 * 
 */
package org.osivia.migration.runners;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.storage.StorageBlob;


/**
 * @author david
 *
 */
public class PostAttachedFileRunner extends AbstractRunner {

    private static final Log log = LogFactory.getLog(PostAttachedFileRunner.class);

    /** Query getting Post having one attched file. */
    private static final String POSTS_WITH_ATTACHED_FILE_QUERY = "select * from Post where post:filename is not null";

    /**
     * Constructor.
     * 
     * @param session
     */
    public PostAttachedFileRunner(CoreSession session) {
        super(session);
    }

    /**
     * Gets Post having one attched file (stored via post schema).
     */
    @Override
    public int setInputs(int limit) {
        int size = 0;

        this.inputs = super.session.query(POSTS_WITH_ATTACHED_FILE_QUERY, limit);
        size = this.inputs.size();

        return size;
    }

    /**
     * Moves blob from post schema to files schema.
     */
    @Override
    public void run() throws ClientException {
        for (DocumentModel post : this.inputs) {
            // Get Blob
            StorageBlob blobProp = (StorageBlob) post.getPropertyValue("post:fileContent");

            if (blobProp != null) {
                // Check blob's properties
                checkBlobProperties(post, blobProp);

                // Setting files property
                ArrayList<Map<String, Serializable>> files = new ArrayList<>(1);

                Map<String, Serializable> file = new HashMap<>(2);
                file.put("filename", blobProp.getFilename());
                file.put("file", blobProp);

                files.add(file);

                post.setPropertyValue("files:files", files);

                // Remove post entry
                post.setPropertyValue("post:fileContent", null);
            }

            // Remove filename in all cases
            post.setPropertyValue("post:filename", null);

            this.session.saveDocument(post);
            this.treatedInputs += 1;

            if (log.isDebugEnabled()) {
                log.debug(post.getPathAsString() + " migrated");
            }
        }

        this.session.save();
    }

    /**
     * Logs possible missing properties.
     * 
     * @param blobProp
     */
    protected void checkBlobProperties(DocumentModel post, StorageBlob blobProp) {
        // // Post treated
        // String postPath = post.getPathAsString();
        //
        // blobProp.
        // // Check
        // for (Entry<String, Serializable> entry : blobProp.entrySet()) {
        // if (entry.getValue() == null) {
        // log.warn("Blob's property [" + entry.getKey() + "] is missing for Post: " + postPath);
        // }
        // }

    }

}
