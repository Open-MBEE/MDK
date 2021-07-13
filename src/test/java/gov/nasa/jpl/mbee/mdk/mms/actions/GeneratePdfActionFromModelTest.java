package gov.nasa.jpl.mbee.mdk.mms.actions;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.tests.MagicDrawTestRunner;
import com.nomagic.magicdraw.uml.BaseElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import gov.nasa.jpl.mbee.mdk.api.MagicDrawHelper;
import gov.nasa.jpl.mbee.mdk.options.MDKOptionsGroup;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Create a PDF from MDK Document Model
 * 1. create PDF from samples/MDK/DocGen.mdzip
 * 2. Using the docbook XML file created by step 1, created PDF.
 *
 * @author Miyako Wilson
 * @JIRA Cameo MDK MDK-64
 */
@RunWith(MagicDrawTestRunner.class)
public class GeneratePdfActionFromModelTest {
    /**
     * Project on which test is performed.
     */
    private static Project project;
    private static File docbookXslFo;
    private static File testProjectFile;

    /**
     * Constructs this test.
     */
    public GeneratePdfActionFromModelTest() {
    }

    @BeforeClass
    public static void setupProject() throws Exception {

        String userdir = System.getProperty("user.dir");
        File docGenProjectFile = new File(userdir + File.separator + "samples" + File.separator + "MDK" + File.separator + "DocGen.mdzip");
        testProjectFile = File.createTempFile("DocGenCopied1", ".mdzip");
        FileUtils.copyFile(docGenProjectFile, testProjectFile);

        MDKOptionsGroup.getMDKOptions().setDefaultValues();
        MDKOptionsGroup.getMDKOptions().setLogJson(true);
        MagicDrawHelper.openProject(testProjectFile);
        project = Application.getInstance().getProject();

        //ClassLoader classLoader = GeneratePDFFromMDModelTest.class.getClassLoader();
        docbookXslFo = new File(userdir + File.separator + "plugins" + File.separator + "gov.nasa.jpl.cae.magicdraw.mdk" + File.separator + "docbook-xsl" + File.separator + "fo" + File.separator + "mdk-default.xsl");
        if (!docbookXslFo.exists()) {
            throw new Exception("\"docbook.xsl\" is not found in mdk plugin.");
        }
    }

    @AfterClass
    public static void closeProject() throws IOException {
        MagicDrawHelper.closeProject();
        testProjectFile.deleteOnExit();
    }

    @Test
    public void init() {
        assertNotNull(project);
        assertTrue(docbookXslFo.exists());
    }

    @Test
    public void testMdkModelToPdfDocGenUsersGuide() {

        try {
            //test Create PDF function


            BaseElement documentElement = project.getElementByID("_18_5_3_8bf0285_1518738536487_780455_42886"); //DocGen
            File outputPdfFile = File.createTempFile(documentElement.getHumanName().substring(documentElement.getHumanType().length()).trim(), ".pdf");
            GeneratePdfAction gp = new GeneratePdfAction((Element) documentElement);
            File autoGeneratedDocbookfile = File.createTempFile(outputPdfFile.getName(), ".xml");
            gp.generate(autoGeneratedDocbookfile, docbookXslFo, outputPdfFile);
            assertTrue(outputPdfFile.exists());
            assertTrue(outputPdfFile.length() > 5000000);//5,374,128 - size is about 1k if pdf is corruptly created
            System.out.println("testPDF file created: " + outputPdfFile.getAbsoluteFile());
            //using DocBook XML file created by the previous test to create pdf file.
            File outputPdfFile2 = File.createTempFile(documentElement.getHumanName().substring(documentElement.getHumanType().length()).trim() + "_fromDocBook", ".pdf");
            GeneratePdfFromDocBookAction gp2 = new GeneratePdfFromDocBookAction(null);//(Element)documentElement);
            gp.generate(autoGeneratedDocbookfile, docbookXslFo, outputPdfFile2);
            assertTrue(outputPdfFile2.exists());
            assertTrue(outputPdfFile2.length() > 5000000);//5,374,128 - size is about 1k if pdf is corruptly created
            System.out.println("testPDF2 file created: " + outputPdfFile2.getAbsoluteFile());

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("exception thrown during this test");
        }

    }

}