package org.hl7.fhir.tools.converters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.SimpleWorkerContext.SimpleWorkerContextBuilder;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionConstraintComponent;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.EvidenceReport;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.utils.FHIRPathEngine;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;

public class StructureDefinitionScanner {

  public static void main(String[] args) throws FHIRException, IOException {
    new StructureDefinitionScanner().run(new File(args[0]));
  }

  private void run(File file) throws FHIRException, IOException {
    System.out.println("Loading");
    FilesystemPackageCacheManager pcm = new FilesystemPackageCacheManager(true);
    NpmPackage npm = pcm.loadPackage("hl7.fhir.r5.core");
    context = new SimpleWorkerContextBuilder().fromPackage(npm);
    fpe = new FHIRPathEngine(context);
    System.out.println("Loaded");
    fix(file);
//    report(sdExt, "Extensions on StructureDefinition");
//    report(sdExtM, "Modifier Extensions on StructureDefinition");
//    report(edExt, "Extensions on ElementDefinition");
//    report(edExtM, "Modifier Extensions on ElementDefinition");
//    report(tdExt, "Extensions on Type Definition");
//    report(sdExt, "Extensions on Binding Definition");
  }

//  private void report(Set<String> set, String title) {
//    System.out.println(title);
//    if (set.size() == 0) {
//      System.out.println("  (none)");      
//    } else {
//      for (String s : set) {
//        System.out.println("* "+s);            
//      }
//    }    
//    System.out.println("");      
//  }

  private IWorkerContext context;
  private FHIRPathEngine fpe;

//  private Set<String> sdExt = new HashSet<>();
//  private Set<String> edExt = new HashSet<>();
//  private Set<String> sdExtM = new HashSet<>();
//  private Set<String> edExtM = new HashSet<>();
//  private Set<String> tdExt = new HashSet<>();
//  private Set<String> bdExt = new HashSet<>();

  private void fix(File file) {

    for (File f : file.listFiles()) {
      if (f.isDirectory()) {
        fix(f);
      } else if (f.getName().endsWith(".xml")) {
        try {
          Resource res = new XmlParser().parse(new FileInputStream(f));
          if (fixResource(res, f.getAbsolutePath())) {
            new XmlParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(f), res); 
          }
        } catch (Exception e) {
        }
      } else if (f.getName().endsWith(".json")) {
        try {
          Resource res = new JsonParser().parse(new FileInputStream(f));
          if (fixResource(res, f.getAbsolutePath())) {
            new JsonParser().setOutputStyle(OutputStyle.PRETTY).compose(new FileOutputStream(f), res); 
          }
        } catch (Exception e) {
        }
      }
    }    
  }

  private boolean fixResource(Resource res, String name) {
    boolean result = false;
    if (res instanceof StructureDefinition) {
      StructureDefinition sd = (StructureDefinition) res;
      if (sd.getDerivation() == TypeDerivationRule.CONSTRAINT) {
        return false;
      }
      System.out.println(sd.getType());
      Map<String, ElementDefinition> map = new HashMap<>();
      for (ElementDefinition ed : sd.getDifferential().getElement()) {
        if (!ed.getCondition().isEmpty()) {
          ed.getCondition().clear();
          result = true;
        }
        map.put(ed.getPath(), ed);
      }
      for (ElementDefinition ed : sd.getDifferential().getElement()) {
        for (ElementDefinitionConstraintComponent inv : ed.getConstraint()) {
          if (inv.hasExpression()) {
            try {
              if ("cnl-1".equals(inv.getKey())) {
                inv.setExpression("exists() implies matches('([^|#])*')");
              }
              Set<ElementDefinition> set = new HashSet<>();
              fpe.check(null, sd.getType(), ed.getPath(), fpe.parse(inv.getExpression()), set);
              for (ElementDefinition edt : set) {
                if (!edt.getPath().equals(ed.getPath()) && map.containsKey(edt.getPath())) {
                  map.get(edt.getPath()).getCondition().add(new IdType(inv.getKey()));
                  result = true;
                }
              }
            } catch (Exception e) {
              System.out.println ("Exception processing "+inv.getKey()+": "+e.getMessage());
            }
          }
        }
      }
      return result;
    } 
    return result;
  }


}
