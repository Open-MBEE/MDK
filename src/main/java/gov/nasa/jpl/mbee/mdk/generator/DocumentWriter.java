package gov.nasa.jpl.mbee.mdk.generator;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.task.ProgressStatus;
import com.nomagic.task.RunnableWithProgress;
import gov.nasa.jpl.mbee.mdk.docgen.docbook.DBBook;
import gov.nasa.jpl.mbee.mdk.docgen.docbook.DBSerializeVisitor;
import gov.nasa.jpl.mbee.mdk.model.DocBookOutputVisitor;
import gov.nasa.jpl.mbee.mdk.model.Document;
import gov.nasa.jpl.mbee.mdk.util.Utils;

import java.io.*;

/**
 * runs the generation as a runnable to not stall magicdraw main thread, also
 * allow user to cancel
 *
 * MD Model -> Document
 * @author dlam
 */
public class DocumentWriter implements RunnableWithProgress {

    private Document dge;
    private File realfile;
    private boolean genNewImage;

    public DocumentWriter(Document dge, File realfile, boolean genNewImage) {
        this.dge = dge;
        this.realfile = realfile;
        this.genNewImage = genNewImage;
    }

    @Override
    public void run(ProgressStatus arg0) {
        arg0.setIndeterminate(true);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(realfile));
            DocBookOutputVisitor visitor = new DocBookOutputVisitor(false, realfile.getParentFile().getAbsolutePath());
            dge.accept(visitor);
            DBBook book = visitor.getBook();
            if (book != null) {
                // List<DocumentElement> books = dge.getDocumentElement();
                DBSerializeVisitor v = new DBSerializeVisitor(genNewImage, realfile.getParentFile(), arg0);
                book.accept(v);
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
                // writer.write("<!DOCTYPE book [\n<!ENTITY % sgml.features \"IGNORE\">\n<!ENTITY % xml.features \"INCLUDE\">\n<!ENTITY % dbcent PUBLIC \"-//OASIS//ENTITIES DocBook Character Entities\nV4.4//EN\" \"dbcentx.mod\">\n%dbcent;\n]>");
                writer.write(v.getOut());
            }
            writer.flush();
            writer.close();
            Utils.guilog("Generation to DocBook Finished");
        } catch (IOException ex) {
           Utils.printException(ex);
        }
    }

}
