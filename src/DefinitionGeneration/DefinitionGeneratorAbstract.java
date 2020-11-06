package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.PropertyValueNamer;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.*;

public class DefinitionGeneratorAbstract extends DefinitionGenerator {

    //private Set<OWLClass> closestPrimitiveAncestors;

    public DefinitionGeneratorAbstract(OWLOntology inputOntology, OntologyReasoningService reasonerService, PropertyValueNamer namer) {
        super(inputOntology, reasonerService, namer);
    }

    public void generateDefinition(OWLClass inputClass) {
        this.generateDefinition(inputClass, new HashSet<RedundancyOptions>());
    }

    public void generateDefinition(OWLClass inputClass, Set<RedundancyOptions> redundancyOptions) {
        Set<OWLClass> ancestors = reasonerService.getAncestorClasses(inputClass);
        System.out.println("Class: " + inputClass + ", ancestors: " + ancestors);
        Set<OWLClass> ancestorRenamedPVs = extractNamedPVs(ancestors);
        System.out.println("Class: " + inputClass + ", ancestor PVs: " + ancestorRenamedPVs);

        Set<OWLClass> primitiveAncestors = new HashSet<OWLClass>();
        primitiveAncestors.addAll(computeClosestPrimitiveAncestors(inputClass));

        primitiveAncestors.removeAll(ancestorRenamedPVs);

        //TODO: needs to be done before rest of redundancy removal, due also to transitivity?
        if(redundancyOptions.contains(RedundancyOptions.eliminateReflexivePVRedundancy) == true) {
            Set<OWLObjectSomeValuesFrom> ancestorPVs = eliminateReflexivePVRedundancies(replaceNamesWithPVs(ancestorRenamedPVs), inputClass);
            ancestorRenamedPVs = replacePVsWithNames(ancestorPVs);
        }

        Set<OWLClass> reducedParentNamedClasses = reduceClassSet(primitiveAncestors);
        Set<OWLObjectSomeValuesFrom> reducedAncestorPVs = replaceNamesWithPVs(reduceClassSet(ancestorRenamedPVs));

        if(redundancyOptions.contains(RedundancyOptions.eliminateRoleGroupRedundancy) == true) {
            reducedAncestorPVs = eliminateRoleGroupRedundancies(reducedAncestorPVs);
        }

        Set<OWLClassExpression> nonRedundantAncestors = new HashSet<OWLClassExpression>();
        nonRedundantAncestors.addAll(reducedParentNamedClasses);
        nonRedundantAncestors.addAll(reducedAncestorPVs);

        constructNecessaryDefinitionAxiom(inputClass, nonRedundantAncestors);

    }

    //possibly quicker than taking all primitive ancestors & redundancy checking?
    public Set<OWLClass> computeClosestPrimitiveAncestors(OWLClass classToDefine) {
        List<OWLClass> currentClassesToExpand = new ArrayList<OWLClass>();
        Set<OWLClass> closestPrimitives = new HashSet<OWLClass>();

        currentClassesToExpand.add(classToDefine);
        System.out.println("Computing primitive ancestors for class: " + classToDefine);
        ListIterator<OWLClass> iterator = currentClassesToExpand.listIterator();
        while(iterator.hasNext()) {
            OWLClass cls = iterator.next();
            Set<OWLClass> parentClasses = reasonerService.getParentClasses(cls);
            Set<OWLClass> namedPVs = extractNamedPVs(parentClasses);
            parentClasses.removeAll(namedPVs);
            System.out.println("Searching for primitive parents of ancestor: " + cls);
            for(OWLClass parent:parentClasses) {
                System.out.println("Checking if ancestor: " + parent + " is primitive");
                //If is primitive, add to returned set
                if(isPrimitive(parent) == true) {
                    System.out.println("...is primitive.");
                    closestPrimitives.add(parent);
                    continue;
                }
                //If not primitive, add to check
                iterator.add(parent);
                iterator.previous();
                System.out.println("...is not primitive.");

            }
        }

        return closestPrimitives;
    }

    public boolean isPrimitive(OWLClass cls) {
        //TODO: for full SCT, could do this using fullyDefined IDs as in toolkit? Quicker?
        System.out.println("equiv axioms for class: " + cls + " are: " + backgroundOntology.getEquivalentClassesAxioms(cls));
        if(backgroundOntology.getEquivalentClassesAxioms(cls).isEmpty()) {
            return true;
        }
        return false;
    }

}
