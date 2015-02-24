package gov.nasa.jpl.mbee.ems.sync;

import gov.nasa.jpl.mbee.ems.ExportUtility;
import gov.nasa.jpl.mbee.ems.ImportUtility;
import gov.nasa.jpl.mbee.ems.validation.ModelValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.GUILog;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.teamwork.application.TeamworkUtils;
import com.nomagic.task.ProgressStatus;
import com.nomagic.task.RunnableWithProgress;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;

public class ManualSyncRunner implements RunnableWithProgress {

    private boolean commit;
    
    private GUILog gl = Application.getInstance().getGUILog();
    
    public ManualSyncRunner(boolean commit) {
        this.commit = commit;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void run(ProgressStatus ps) {
        Application.getInstance().getGUILog().log("[INFO] Getting changes from MMS...");
        Project project = Application.getInstance().getProject();
        
        AutoSyncCommitListener listener = AutoSyncProjectListener.getCommitListener(Application.getInstance().getProject());
        if (listener == null)
            return; //some error here
        listener.disable();
        Map<String, Set<String>> jms = AutoSyncProjectListener.getJMSChanges(Application.getInstance().getProject());
        listener.enable();
        if (jms == null)
            return;
        Map<String, Element> localAdded = listener.getAddedElements();
        Map<String, Element> localDeleted = listener.getDeletedElements();
        Map<String, Element> localChanged = listener.getChangedElements();
        
        Set<String> webChanged = jms.get("changed");
        Set<String> webAdded = jms.get("added");
        Set<String> webDeleted = jms.get("deleted");
        
        Set<String> toGet = new HashSet<String>(webChanged);
        toGet.addAll(webAdded);
        
        Set<String> cannotAdd = new HashSet<String>();
        Set<String> cannotChange = new HashSet<String>();
        Set<String> cannotDelete = new HashSet<String>();
        
        if (!toGet.isEmpty()) {
            TeamworkUtils.lockElement(project, project.getModel(), true);
            JSONObject getJson = new JSONObject();
            JSONArray getElements = new JSONArray();
            getJson.put("elements", getElements);
            for (String e: toGet) {
                JSONObject el = new JSONObject();
                el.put("sysmlid", e);
                getElements.add(el);
            }
            String url = ExportUtility.getUrlWithWorkspace();
            url += "/elements";
            gl.log("[INFO] Getting " + getElements.size() + " elements from MMS.");
            String response = ExportUtility.getWithBody(url, getJson.toJSONString());
            if (response == null)
                return; //bad
            Map<String, JSONObject> webElements = new HashMap<String, JSONObject>();
            JSONObject webObject = (JSONObject)JSONValue.parse(response);
            JSONArray webArray = (JSONArray)webObject.get("elements");
            for (Object o: webArray) {
                String webId = (String)((JSONObject)o).get("sysmlid");
                webElements.put(webId, (JSONObject)o);
            }
            //if (webElements.size() != toGet.size())
            //    return; //??
                
            //calculate order to create web added elements
            List<JSONObject> webAddedObjects = new ArrayList<JSONObject>();
            for (String webAdd: webAdded) {
                if (webElements.containsKey(webAdd))
                    webAddedObjects.add(webElements.get(webAdd));
            }
            List<JSONObject> webAddedSorted = ImportUtility.getCreationOrder(webAddedObjects);
            
            //calculate potential conflicted set and clean web updated set
            Set<String> localChangedIds = new HashSet<String>(localChanged.keySet());
            localChangedIds.retainAll(webChanged);
            JSONArray webConflictedObjects = new JSONArray();
            Set<Element> localConflictedElements = new HashSet<Element>();
            if (!localChangedIds.isEmpty()) {
                for (String conflictId: localChangedIds) {
                    if (webElements.containsKey(conflictId)) {
                        webConflictedObjects.add(webElements.get(conflictId));
                        localConflictedElements.add(localChanged.get(conflictId));
                    }
                }
            }
            //find web changed that are not conflicted
            List<JSONObject> webChangedObjects = new ArrayList<JSONObject>();
            for (String webUpdate: webChanged) {
                if (localChangedIds.contains(webUpdate))
                    continue;
                if (webElements.containsKey(webUpdate))
                    webChangedObjects.add(webElements.get(webUpdate));
            }
            
            gl.log("[INFO] Applying changes...");
            SessionManager sm = SessionManager.getInstance();
            sm.createSession("mms delayed sync change");
            try {
                //take care of web added
                if (webAddedSorted != null) {
                    for (Object element : webAddedSorted) {
                        ImportUtility.createElement((JSONObject) element, false);
                    }
                    for (Object element : webAddedSorted) { 
                        try {
                            Element newe = ImportUtility.createElement((JSONObject) element, true);
                            gl.log("[SYNC] " + newe.getHumanName() + " created.");
                        } catch (Exception ex) {
                            cannotAdd.add((String)((JSONObject)element).get("sysmlid"));
                        }
                    }
                } else {
                    for (Object element: webAddedObjects) {
                        cannotAdd.add((String)((JSONObject)element).get("sysmlid"));
                    }
                }
            
                //take care of updated
                for (JSONObject webUpdated: webChangedObjects) {
                    Element e = ExportUtility.getElementFromID((String)webUpdated.get("sysmlid"));
                    if (e == null) {
                        //bad? maybe it was supposed to have been added?
                        continue;
                    }
                    try {
                        ImportUtility.updateElement(e, webUpdated);
                        ImportUtility.setOwner(e, webUpdated);
                        gl.log("[SYNC] " + e.getHumanName() + " updated.");
                    } catch (Exception ex) {
                        cannotChange.add(ExportUtility.getElementID(e));
                    }
                }
                
                //take care of deleted
                for (String e: webDeleted) {
                    Element toBeDeleted = ExportUtility.getElementFromID(e);
                    if (toBeDeleted == null)
                        continue;
                    try {
                        ModelElementsManager.getInstance().removeElement(toBeDeleted);
                        gl.log("[SYNC] " + toBeDeleted.getHumanName() + " deleted.");
                    } catch (Exception ex) {
                        cannotDelete.add(e);
                    }
                }
                listener.disable();
                sm.closeSession();
                listener.enable();
                gl.log("[INFO] Finished applying changes.");
                
                if (!cannotAdd.isEmpty() || !cannotChange.isEmpty() || !cannotDelete.isEmpty()) {
                    JSONObject failed = new JSONObject();
                    JSONArray failedAdd = new JSONArray();
                    failedAdd.addAll(cannotAdd);
                    JSONArray failedChange = new JSONArray();
                    failedChange.addAll(cannotChange);
                    JSONArray failedDelete = new JSONArray();
                    failedDelete.addAll(cannotDelete);
                    failed.put("changed", failedChange);
                    failed.put("added", failedAdd);
                    failed.put("deleted", failedDelete);
                    listener.disable();
                    sm.createSession("failed changes");
                    try {
                        AutoSyncProjectListener.setFailed(project, failed);
                        sm.closeSession();
                    } catch (Exception ex) {
                        sm.cancelSession();
                    }
                    listener.enable();
                    gl.log("[WARNING] There were changes that couldn't be applied.");
                }
                
              //conflicts???
                JSONObject mvResult = new JSONObject();
                mvResult.put("elements", webConflictedObjects);
                ModelValidator mv = new ModelValidator(null, mvResult, false, localConflictedElements, false);
                mv.validate(false, null);
                Set<Element> conflictedElements = mv.getDifferentElements();
                if (!conflictedElements.isEmpty()) {
                    JSONObject conflictedToSave = new JSONObject();
                    JSONArray conflictedElementIds = new JSONArray();
                    for (Element ce: conflictedElements)
                        conflictedElementIds.add(ExportUtility.getElementID(ce));
                    conflictedToSave.put("elements", conflictedElementIds);
                    gl.log("[INFO] There are potential conflicts between changes from MMS and locally changed elements, please resolve first and rerun update/commit.");
                //?? popups or validation window?
                    listener.disable();
                    sm.createSession("failed changes");
                    try {
                        AutoSyncProjectListener.setLooseEnds(project, conflictedToSave);
                        sm.closeSession();
                    } catch (Exception ex) {
                        sm.cancelSession();
                    }
                    listener.enable();
                    mv.showWindow();
                    return;
                } 
            } catch (Exception e) {
                sm.cancelSession();
            }
        } else {
            gl.log("[INFO] MMS has no updates.");
            //AutoSyncProjectListener.setLooseEnds(project, null);
            //AutoSyncProjectListener.setFailed(project, null);
        }
        
        //send local changes
        if (commit) {
            gl.log("[INFO] Committing local changes to MMS...");
            JSONArray toSendElements = new JSONArray();
            for (Element e: localAdded.values()) {
                toSendElements.add(ExportUtility.fillElement(e, null));
            }
            for (Element e: localChanged.values()) {
                toSendElements.add(ExportUtility.fillElement(e, null));
            }
            JSONObject toSendUpdates = new JSONObject();
            toSendUpdates.put("elements", toSendElements);
            toSendUpdates.put("source", "magicdraw");
            if (toSendElements.size() > 100) {
                
            }
            //do foreground?
            if (!toSendElements.isEmpty())
                OutputQueue.getInstance().offer(new Request(ExportUtility.getPostElementsUrl(), toSendUpdates.toJSONString(), "POST", true));
            localAdded.clear();
            localChanged.clear();
            
            JSONArray toDeleteElements = new JSONArray();
            for (String e: localDeleted.keySet()) {
                JSONObject toDelete = new JSONObject();
                toDelete.put("sysmlid", e);
                toDeleteElements.add(toDelete);
            }
            toSendUpdates.put("elements", toDeleteElements);
            if (!toDeleteElements.isEmpty())
                OutputQueue.getInstance().offer(new Request(ExportUtility.getUrlWithWorkspace() + "/elements", toSendUpdates.toJSONString(), "DELETEALL", true));
            localDeleted.clear();
            if (toDeleteElements.isEmpty() && toSendElements.isEmpty())
                gl.log("[INFO] No changes to commit.");
            else
                gl.log("[INFO] Don't forget to save or commit to teamwork and unlock!");
            //commit automatically and send project version?
        }
        
    }

}