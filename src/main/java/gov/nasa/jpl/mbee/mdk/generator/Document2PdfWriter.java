package gov.nasa.jpl.mbee.mdk.generator;

import com.nomagic.task.ProgressStatus;

import java.io.File;

/**
 * runs the generation as a runnable to not stall magicdraw main thread, also
 * allow user to cancel
 * <p>
 * Document(xml) -> PDF
 *
 * @author mwilson
 */
public class Document2PdfWriter extends DocumentAndPdfWriter {

    protected File docbook;

    public Document2PdfWriter(File docbook, File docbookXslFo, File outputFile) {
        super(docbook, docbookXslFo, outputFile);
    }

    @Override
    public void run(ProgressStatus status) {
        status.setIndeterminate(true);
        status.setDescription("Generating a PDF file...");
        document2Pdf();
    }
}
