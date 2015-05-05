package gov.nasa.jpl.mbee.viewedit;

import gov.nasa.jpl.mbee.ems.ExportUtility;
import gov.nasa.jpl.mgss.mbee.docgen.docbook.DBList;
import gov.nasa.jpl.mgss.mbee.docgen.docbook.DBListItem;
import gov.nasa.jpl.mgss.mbee.docgen.docbook.DBParagraph;
import gov.nasa.jpl.mgss.mbee.docgen.docbook.DBTable;
import gov.nasa.jpl.mgss.mbee.docgen.docbook.DBText;
import gov.nasa.jpl.mgss.mbee.docgen.docbook.DocumentElement;

import java.util.HashSet;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class DBAlfrescoListVisitor extends DBAlfrescoVisitor {

    private JSONObject listjson;
    private Set<String>  listelements;
    private JSONArray  curitem;

    public DBAlfrescoListVisitor(boolean recurse, JSONObject e) {
        super(recurse);
        this.elements = e;
        listjson = new JSONObject();
        listelements = new HashSet<String>();
    }

    public JSONObject getObject() {
        return listjson;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void visit(DBList list) {
        if (listjson.containsKey("type")) {
            DBAlfrescoListVisitor inner = new DBAlfrescoListVisitor(recurse, this.elements);
            list.accept(inner);
            curitem.add(inner.getObject());
            listelements.addAll(inner.getListElements());
            elementSet.addAll(inner.getElementSet());
        } else {
            listjson.put("type", "List");
            if (list.isOrdered())
                listjson.put("ordered", true);
            else
                listjson.put("ordered", false);
            listjson.put("bulleted", true);
            JSONArray l = new JSONArray();
            listjson.put("list", l);
            for (DocumentElement de: list.getChildren()) {
                curitem = new JSONArray();
                de.accept(this);
                l.add(curitem);
            }
        }
    }

    @Override
    public void visit(DBListItem listitem) {
        for (DocumentElement de: listitem.getChildren()) {
            de.accept(this);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void visit(DBParagraph para) {
        JSONObject o = getJSONForDBParagraph(para);
        if (para.getFrom() != null && para.getFromProperty() != null) {
            if (ExportUtility.shouldAdd(para.getFrom()))
                this.listelements.add(ExportUtility.getElementID(para.getFrom()));
        }
        curitem.add(o);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void visit(DBText text) {
        JSONObject o = getJSONForDBText(text);
        if (text.getFrom() != null && text.getFromProperty() != null) {
            if (ExportUtility.shouldAdd(text.getFrom()))
                this.listelements.add(ExportUtility.getElementID(text.getFrom()));
        }
        curitem.add(o);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void visit(DBTable table) {
        DBAlfrescoTableVisitor v = new DBAlfrescoTableVisitor(this.recurse, this.elements);
        table.accept(v);
        listelements.addAll(v.getTableElements());
        elementSet.addAll(v.getElementSet());
        curitem.add(v.getObject());
    }

    public Set<String> getListElements() {
        return listelements;
    }
}

