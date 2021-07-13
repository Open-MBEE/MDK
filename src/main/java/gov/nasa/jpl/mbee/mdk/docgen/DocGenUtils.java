package gov.nasa.jpl.mbee.mdk.docgen;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.export.image.ImageExporter;
import com.nomagic.magicdraw.properties.BooleanProperty;
import com.nomagic.magicdraw.properties.ElementProperty;
import com.nomagic.magicdraw.properties.NumberProperty;
import com.nomagic.magicdraw.properties.StringProperty;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import gov.nasa.jpl.mbee.mdk.docgen.docbook.DocumentElement;
import gov.nasa.jpl.mbee.mdk.docgen.view.ViewElement;
import gov.nasa.jpl.mbee.mdk.util.MDUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import javax.xml.transform.TransformerException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocGenUtils {

    /**
     * this was trying to use regex only to convert html tags to docbook tags,
     * should probably switch to just using jsoup
     */
    @SuppressWarnings("serial")
    public static final Map<String, String> html2docbookConvert = new HashMap<String, String>() {
        {
            put("<p>|<p [^>]*>", "<para>");
            put("</p>", "</para>");
            put("<ul>|<ul [^>]*>",
                    "<itemizedlist spacing=\"compact\">");
            put("</ul>", "</itemizedlist>");
            put("<ol>|<ol [^>]*>",
                    "<orderedlist spacing=\"compact\">");
            put("</ol>", "</orderedlist>");
            put("<li>|<li [^>]*>",
                    "<listitem><para>");
            put("</li>", "</para></listitem>");
            put("<b>|<b [^>]*>|<em>|<em [^>]*>|<strong>|<strong [^>]*>",
                    "<emphasis role=\"bold\">");
            put("<h[1-6]>|<h[1-6] [^>]*>", "<para><emphasis role=\"bold\">");
            put("<s>|<strike>|<s [^>]*>|<strike [^>]*>",
                    "<emphasis role=\"strikethrough\">");
            put("<i>|<i [^>]*>", "<emphasis>");
            put("<u>|<u [^>]*>",
                    "<emphasis role=\"underline\">");
            put("<span>|<span [^>]*>|</span>|<br>|<br/>|</br>|<br />",
                    "");
            put("</b>|</i>|</u>|</strong>|</em>|</s>|</strike>",
                    "</emphasis>");
            put("</h[1-6]>", "</emphasis></para>");
            put("<font [^>]*>|</font>", "");
            put("<sup>|<sup [^>]*>",
                    "<superscript>");
            put("<sub>|<sub [^>]*>",
                    "<subscript>");
            put("</sup>", "</superscript>");
            put("</sub>", "</subscript>");
            put("<a href=\"(http[^\"]+)\">([^<]*)</a>",
                    "<link xlink:href=\"$1\">$2</link>");
            put("<a href=\"(file[^\"]+)\">([^<]*)</a>",
                    "<link xlink:href=\"$1\">$2</link>");
            put("<a href=\"(mailto[^\"]+)\">([^<]*)</a>",
                    "<link xlink:href=\"$1\">$2</link>");
            put("<a href=\"mdel://([^\"&^\\?]+)(\\?[^\"]*)?\">([^<]*)</a>",
                    "<link linkend=\"$1\">$3</link>");
            put("<img [^>]*src=\"([^>]+)\"[^>]*></img>", "<imageobject><imagedata fileref=\"$1\" scalefit=\"1\"/></imageobject>");
            put("<img [^>]*src=\"([^>]+)\"[^>]*/>", "<imageobject><imagedata fileref=\"$1\" scalefit=\"1\"/></imageobject>");
            put("<pre>|<pre [^>]*>", "<screen>");
            put("</pre>", "</screen>");
            put("<svg",
                    "<mediaobject><imageobject><imagedata><svg");
            put("</svg>",
                    "</svg></imagedata></imageobject></mediaobject>");
            put("&nbsp;", "&#160;");
            put("&sect;", "&#167;");
            put("&sup2;",
                    "<superscript>2</superscript>");
            put("&sup3;",
                    "<superscript>3</superscript>");
        }
    };
    private static final Pattern ENTITY_PATTERN = Pattern.compile("(&[^\\s]+?;)");

    public static int DOCGEN_DIAGRAM_DPI = 72;
    public static int DOCGEN_DIAGRAM_SCALE_PERCENT = 100;

    /**
     * docbook ignores regular white space in table cells, this is to force
     * indentation in docbook, 1 indent is 4 spaces
     *
     * @param name
     * @param depth
     * @return
     */
    public static String getIndented(String name, int depth) {
        String space = "";
        for (int i = 1; i < depth; i++) {
            space += "&#xA0;&#xA0;&#xA0;&#xA0;";
        }
        return space + name;
    }

    /**
     * Convert a String of HTML with named HTML entities to the
     * same String with entities converted to numbered XML entities
     *
     * @param html
     * @return xml
     */
    public static String htmlToXmlEntities(String html) {
        StringBuffer stringBuffer = new StringBuffer();
        Matcher matcher = ENTITY_PATTERN.matcher(html);
        
        while (matcher.find()) {
            String replacement = null;
            if (matcher.group(1).equals("&nbsp;")) {
                replacement = "&#160;"; //line 1766 and others of DocGen.mdzip works with this except the line 402
            }
            else {
                replacement = htmlEntityToXmlEntity(matcher.group(1));
            }
            matcher.appendReplacement(stringBuffer, "");
            stringBuffer.append(replacement);
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    /**
     * Replace an HTML entity with an XML entity
     *
     * @param html
     * @return xml
     */
    private static String htmlEntityToXmlEntity(String html) {
        return StringEscapeUtils.escapeXml11(StringEscapeUtils.unescapeHtml4(html));
    }

    /**
     * given any object tries to return a string representation suitable for use
     * in docbook<br/>
     *
     * @param s
     * @return
     */
    public static String fixString(Object s) {
        return fixString(s, true);
    }

    public static String fixString(Object s, boolean convertHtml) {
        String rv;
        // may want to look at
        // com.nomagic.magicdraw.uml.RepresentationTextCreator.getRepresentedText
        if (s instanceof String) {
            if (((String) s).contains("<html>")){
                if (convertHtml) {
                    return htmlToXmlEntities((String) s);
                }
                else {
                    return gov.nasa.jpl.mbee.mdk.util.Utils.stripHtmlWrapper((String) s);
                }
            }
            else {
                  	return htmlToXmlEntities(((String) s)
                            .replaceAll("&(?![A-Za-z#0-9]+;)", "&amp;").replaceAll("<([>=\\s])", "&lt;$1")
                            .replaceAll("<<", "&lt;&lt;").replaceAll("<(?![^>]+>)", "&lt;"))
                            .replaceAll("[^\\x00-\\x7F]", "");  //for line 402 of DocGen.mdzip
            }
        }
        else if (s instanceof Integer) {
            return Integer.toString((Integer) s);
        }
        else if (s instanceof InstanceValue) {
            InstanceSpecification is = ((InstanceValue) s).getInstance();
            if (is != null) {
                return fixString(is.getName());
            }
        }
        else if (s instanceof ElementValue) {
            Element e = ((ElementValue) s).getElement();
            return fixString(e);
        }
        else if (s instanceof LiteralBoolean) {
            return Boolean.toString(((LiteralBoolean) s).isValue());
        }
        else if (s instanceof LiteralString) {
            return fixString(((LiteralString) s).getValue());
        }
        else if (s instanceof LiteralInteger) {
            return Integer.toString(((LiteralInteger) s).getValue());
        }
        else if (s instanceof LiteralUnlimitedNatural) {
            return Integer.toString(((LiteralUnlimitedNatural) s).getValue());
        }
        else if (s instanceof LiteralReal) {
            return Double.toString(((LiteralReal) s).getValue());
        }
        else if ((rv = getRestrictedValue(s)) != null) {
            return rv;
        }
        else if (s instanceof NamedElement) {
            return fixString(((NamedElement) s).getName());
        }
        else if (s instanceof Comment) {
            return fixString(((Comment) s).getBody());
        }
        else if (s instanceof StringProperty) {
            return fixString(((StringProperty) s).getString());
        }
        else if (s instanceof NumberProperty) {
            return ((NumberProperty) s).getValue().toString();
        }
        else if (s instanceof BooleanProperty) {
            return ((BooleanProperty) s).getBooleanObject().toString();
        }
        else if (s instanceof ElementProperty) {
            return fixString(((ElementProperty) s).getElement());
        }
        else if (s instanceof Slot) {
            return slot2String((Slot) s);
        }
        else if (s != null) {
            return fixString(s.toString());
        }
        return "";
    }

    //in case Expression is used other than RestrictedValue, only considered as RestrictedValue when 1st operand is LiteralString with "RestrictedValue"
    private static String getRestrictedValue(Object s) {
        if (s instanceof Expression) { //Expression is NamedElement
            List<ValueSpecification> ves = ((Expression) s).getOperand();
            if (ves.size() > 0 && ves.get(0) instanceof LiteralString) {
                if (((LiteralString) ves.get(0)).getValue().compareTo("RestrictedValue") == 0) { //then assumed to be RestrictedValue
                    // ves.size() == 3 (LiteralString ("RestrictedValue"), ElementValue, Expression) - see CreatedRestrictedValueAction.actionPerformed
                    if (ves.size() == 3 && ves.get(1) instanceof ElementValue) {
                        return fixString((((NamedElement) ((ElementValue) ves.get(1)).getElement()).getName()));
                    }
                    else {
                        return "malformed restricted value";
                    }
                }
            }
        }
        return null; //assume not to be a RestrictedValue
    }

    public static Object getLiteralValue(Object s, boolean convertHtml) {
        if (s instanceof String) {
            return fixString(s, convertHtml);
        }
        else if (s instanceof Integer) {
            return s;
        }
        else if (s instanceof InstanceValue) {
            InstanceSpecification is = ((InstanceValue) s).getInstance();
            if (is != null) {
                return getLiteralValue(is.getName(), convertHtml);
            }
        }
        else if (s instanceof ElementValue) {
            Element e = ((ElementValue) s).getElement();
            return getLiteralValue(e, convertHtml);
        }
        else if (s instanceof LiteralBoolean) {
            return ((LiteralBoolean) s).isValue();
        }
        else if (s instanceof LiteralString) {
            return ((LiteralString) s).getValue();
        }
        else if (s instanceof LiteralInteger) {
            return ((LiteralInteger) s).getValue();
        }
        else if (s instanceof LiteralUnlimitedNatural) {
            return ((LiteralUnlimitedNatural) s).getValue();
        }
        else if (s instanceof LiteralReal) {
            return ((LiteralReal) s).getValue();
        }
        else if (s instanceof NamedElement) {
            return ((NamedElement) s).getName();
        }
        else if (s instanceof Comment) {
            return getLiteralValue(((Comment) s).getBody(), convertHtml);
        }
        else if (s instanceof StringProperty) {
            return getLiteralValue(((StringProperty) s).getString(), convertHtml);
        }
        else if (s instanceof NumberProperty) {
            return ((NumberProperty) s).getValue();
        }
        else if (s instanceof BooleanProperty) {
            return ((BooleanProperty) s).getBooleanObject();
        }
        else if (s instanceof ElementProperty) {
            return getLiteralValue(((ElementProperty) s).getElement(), convertHtml);
        }
        else if (s instanceof Slot) {
            return slot2String((Slot) s);
        }
        return s;
    }

    /**
     * gives sensible text representation for slot element
     *
     * @param s
     * @return
     */
    public static String slot2String(Slot s) {
        return slot2String(s, true);
    }

    public static String slot2String(Slot s, boolean includeName) {
        String string = (includeName ? s.getDefiningFeature().getName() + " = " : "");
        List<String> values = new ArrayList<String>();
        for (ValueSpecification vs : s.getValue()) {
            values.add(fixString(vs));
        }
        return string + gov.nasa.jpl.mbee.mdk.util.Utils.join(values, ", ");
    }

    /**
     * tries to make s into a docbook paragraph if not already one
     *
     * @param s
     * @return
     */
    public static String addDocbook(String s) {
        String ss = html2docbook(s);
        if (ss.matches("(?s).*<para>.*")) // (s?) is the flag for DOTALL mode, .
        // doesn't match newlines by default
        // if (s.matches("(?s)\\s*<para>.*</para>\\s*"))
        {
            return ss;
        }
        return "<para>" + ss + "</para>";
    }

    /**
     * tries to make s into a html paragraph if not already one
     *
     * @param s
     * @return
     */
    public static String addP(String s) {
        if (s.matches("(?s).*<p>.*")) // (s?) is the flag for DOTALL mode, .
        // doesn't match newlines by default
        // if (s.matches("(?s)\\s*<para>.*</para>\\s*"))
        {
            return s;
        }
        return "<p>" + s + "</p>";
    }

    /**
     * this is to help pdf transfrom be able to do wordwrap at non whitespace
     * chars, adds an invisible space to chars that should be able to break
     * probably should use some regex instead...
     *
     * @param s
     * @return
     */
    public static String addInvisibleSpace(String s) {
        return s.replaceAll(";", ";&#x200B;").replaceAll("\\.", ".&#x200B;").replaceAll("\\(", "(&#x200B;")
                .replaceAll("\\)", ")&#x200B;").replaceAll(",", ",&#x200B;").replaceAll("/", "/&#x200B;")
                .replaceAll("_", "_&#x200B;").replaceAll("::", "::&#x200B;");
    }

    /**
     * if string contains html, converts it to docbook<br/>
     * also does some special processing if there's informal table elements,
     * removes width on tables so pdf transforms don't get cut off if width is
     * set too big should use jsoup processing to replace the regex map above
     *
     * @param html
     * @return
     */
    public static String html2docbook(String html) {
        if (!html.contains("<html>")) {
            return html;
        }
        String s = null;
        Document d = Jsoup.parse(html);
        Elements tables = d.select("table.informal");
        if (!tables.isEmpty()) {
            tables.tagName("informaltable");
            tables.removeAttr("width");
        }
        tables = d.select("table");
        tables.removeAttr("width");

        Elements paragraphs = d.select("p");
        for (org.jsoup.nodes.Element e : paragraphs) {
            if (!e.hasText() || e.html().equals("&#160;") || e.html().equals("&nbsp;")) {
                e.remove();
            }
        }
        for (org.jsoup.nodes.Element e : d.select("span")) {
            if (e.hasAttr("style") && e.attr("style").startsWith("background-color")) {
                String style = e.attr("style");
                String color = style.substring(style.indexOf('#') + 1);
                e.tagName("phrase");
                e.removeAttr("style");
                e.attr("role", color);
            }
        }
        s = d.toString();
        int start = s.indexOf("<body>");
        int end = s.indexOf("</body>");
        if (start > -1 && end > -1) {
            s = s.substring(start + 6, end);
        }
        for (String key : html2docbookConvert.keySet()) {
            s = s.replaceAll(key, html2docbookConvert.get(key));
        }
        return s;
    }

    /**
     * Generates svg and png files of the diagram passed in, the diagram names
     * will be the diagram id in magicdraw. The images will be 72 dpi and 100%
     * scaling.
     *
     * @param diagram   the magicdraw diagram element
     * @param directory directory for docbook xml output (without trailing slash)
     * @throws IOException
     */
    public static List<String> exportDiagram(Diagram diagram, File directory) throws IOException {
        Project project = Application.getInstance().getProject();
        if (project == null) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        DiagramPresentationElement diagramPresentationElement = project.getDiagram(diagram);
        if (diagramPresentationElement == null) {
            return Collections.emptyList();
        }

        String pngFileName = diagramPresentationElement.getID() + ".png";
        String svgFileName = diagramPresentationElement.getID() + ".svg";
        File pngDiagramFile = new File(directory, pngFileName);
        File svgDiagramFile = new File(directory, svgFileName);
        results.add(directory.getName() + "/" + svgFileName);

        try {
            MDUtils.exportSVG(svgDiagramFile, diagramPresentationElement);
            ImageExporter.export(diagramPresentationElement, ImageExporter.PNG, pngDiagramFile, false, DOCGEN_DIAGRAM_DPI, DOCGEN_DIAGRAM_SCALE_PERCENT);
        } catch (IOException | TransformerException e) {
            e.printStackTrace();
            return results;
        }

        // whether to scale to width or not, in svg file width is specified in
        // inches, check it's less than width of pdf portrait paper
        boolean scale = true;
        try (BufferedReader reader = new BufferedReader(new FileReader(svgDiagramFile))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith("<svg")) {
                    int widthindex = line.indexOf("width");
                    if (widthindex > -1) {
                        int end = line.indexOf("\"", widthindex + 7);
                        String w = line.substring(widthindex + 7, end - 2);
                        double wd = Double.parseDouble(w);
                        if (wd < 5.5) {
                            scale = false;
                        }
                    }
                    break;
                }
                line = reader.readLine();
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        results.add(Boolean.toString(scale));
        return results;
    }

    /**
     * return names of a collection of named elements
     *
     * @param elements
     * @return
     */
    public static List<String> getElementNames(Collection<NamedElement> elements) {
        List<String> names = new ArrayList<String>();
        for (NamedElement e : elements) {
            names.add(e.getName());
        }
        return names;
    }

    public static DocumentElement ecoreTranslateView(ViewElement ve, boolean forViewEditor) {
        DocumentElement de = null;
        de = (new DocGenViewDBSwitch(forViewEditor)).doSwitch(ve);
        return de;
    }
}
