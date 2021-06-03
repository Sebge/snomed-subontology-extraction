package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.IntroducedNameHandler;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.*;

public class DefinitionGeneratorAbstract extends DefinitionGenerator {

    public DefinitionGeneratorAbstract(OWLOntology inputOntology, OntologyReasoningService reasonerService, IntroducedNameHandler namer) {
        super(inputOntology, reasonerService, namer);
    }

    //TODO: refactor, move to super (?)
    public void generateDefinition(OWLClass inputClass) {
        //default: all redundancy removed
        Set<RedundancyOptions> defaultOptions = new HashSet<RedundancyOptions>();
        defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
        defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
        this.generateDefinition(inputClass, defaultOptions);
        this.generateDefinition(inputClass, defaultOptions);
    }

    //TODO: move to super, code duplication with NNF
    public void generateDefinition(OWLClass classToDefine, Set<RedundancyOptions> redundancyOptions) {
        //separate ancestors into classes and PVs (represented by new name classes)
        Set<OWLClass> ancestors = reasonerService.getAncestors(classToDefine);
        Set<OWLClass> ancestorRenamedPVs = extractNamedPVs(ancestors);
        Set<OWLClass> primitiveAncestors = new HashSet<OWLClass>();
        primitiveAncestors.addAll(computeClosestPrimitiveAncestors(classToDefine));

        //remove classes representing introduced names
        primitiveAncestors.removeAll(ancestorRenamedPVs);
        primitiveAncestors.removeAll(extractNamedGCIs(primitiveAncestors));

        //GCI handling: computing authoring form of GCI requires naming the LHS, meaning GCIName <= originalGCIClass, which is undesirable.
        if(namer.isNamedGCI(classToDefine)) { //TODO: 31/05/21 -- still needed?
            OWLClass originalGCIConcept = namer.retrieveSuperClassFromNamedGCI(classToDefine);
            primitiveAncestors.remove(originalGCIConcept);
            primitiveAncestors.addAll(computeClosestPrimitiveAncestors(originalGCIConcept));
        }

        Set<OWLClass> reducedParentNamedClasses = new HashSet<OWLClass>();
        Set<OWLObjectSomeValuesFrom> reducedAncestorPVs = new HashSet<OWLObjectSomeValuesFrom>();

        //if(redundancyOptions.contains(RedundancyOptions.eliminateReflexivePVRedundancy)) {
        //    Set<OWLObjectSomeValuesFrom> ancestorPVs = eliminateReflexivePVRedundancies(replaceNamesWithPVs(ancestorRenamedPVs), inputClass);
        //    ancestorRenamedPVs = replacePVsWithNames(ancestorPVs); //t
        //}
        if(redundancyOptions.contains(RedundancyOptions.eliminateLessSpecificRedundancy)) {
            reducedParentNamedClasses = reduceClassSet(primitiveAncestors);
            System.out.println("Parents before GCI check: " + reducedParentNamedClasses);

            boolean gciParentsChanged = false;
            if(redundancyOptions.contains(RedundancyOptions.eliminateSufficientProximalGCIs)) {
                System.out.println("Eliminating sufficient proximal GCI concepts");
                Set<OWLClass> parentsAfterCheckingGCIs = eliminateSufficientProximalGCIConcepts(classToDefine, reducedParentNamedClasses);
                parentsAfterCheckingGCIs = reduceClassSet(parentsAfterCheckingGCIs); //TODO: unnecessary additional call?

                if(!parentsAfterCheckingGCIs.equals(reducedParentNamedClasses)) {
                    gciParentsChanged = true;
                    reducedParentNamedClasses = parentsAfterCheckingGCIs;
                }
            }
            //if parents changed, then eliminate PVs inherited from type 1 gci concepts.
            System.out.println("Parents after gci check: " + reducedParentNamedClasses);
            if(gciParentsChanged) {
                System.out.println("GCI parents changed for class: " + classToDefine);
                Set<OWLClass> pvsToCheck = new HashSet<>();
                pvsToCheck.addAll(ancestorRenamedPVs);
                for(OWLClass pv:pvsToCheck) {
                    System.out.println("Checking pv: " + pv);
                    boolean pvInheritedFromTypeOneGCI = true;
                    for(OWLClass parent:reducedParentNamedClasses) {
                        System.out.println("Parent check against: " + parent);
                        System.out.println("Ancestors of parent: " + reasonerService.getAncestors(parent));
                        //if an ancestor of a retained parent, or a direct ancestor of the class being defined, keep.
                        if(reasonerService.getAncestors(parent).contains(pv) || reasonerService.getDirectAncestors(classToDefine).contains(pv)) {
                            pvInheritedFromTypeOneGCI = false;
                        }
                    }
                    if(pvInheritedFromTypeOneGCI) {
                        ancestorRenamedPVs.remove(pv);
                    }
                }
            }
            reducedAncestorPVs = replaceNamesWithPVs(reduceClassSet(ancestorRenamedPVs));
        }
        else {
            reducedParentNamedClasses = primitiveAncestors;
            reducedAncestorPVs = replaceNamesWithPVs(ancestorRenamedPVs);
        }
        if(redundancyOptions.contains(RedundancyOptions.eliminateRoleGroupRedundancy)) {
            reducedAncestorPVs = eliminateRoleGroupRedundancies(reducedAncestorPVs);
        }
        if(redundancyOptions.contains(RedundancyOptions.eliminateReflexivePVRedundancy)) {
            reducedAncestorPVs = eliminateReflexivePVRedundancies(classToDefine, reducedAncestorPVs);
        }

        Set<OWLClassExpression> nonRedundantAncestors = new HashSet<OWLClassExpression>();
        nonRedundantAncestors.addAll(reducedParentNamedClasses);
        nonRedundantAncestors.addAll(reducedAncestorPVs);

        constructDefinitionAxiom(classToDefine, nonRedundantAncestors);
    }

    //possibly quicker than taking all primitive ancestors & redundancy checking?
    private Set<OWLClass> computeClosestPrimitiveAncestors(OWLClass classToDefine) {
        List<OWLClass> currentClassesToExpand = new ArrayList<OWLClass>();
        Set<OWLClass> closestPrimitives = new HashSet<OWLClass>();

        currentClassesToExpand.add(classToDefine);

        ListIterator<OWLClass> iterator = currentClassesToExpand.listIterator();
        while(iterator.hasNext()) {
            OWLClass cls = iterator.next();
            Set<OWLClass> parentClasses = reasonerService.getDirectAncestors(cls);
            Set<OWLClass> namedPVs = extractNamedPVs(parentClasses);
            parentClasses.removeAll(namedPVs);
            for(OWLClass parent:parentClasses) {
                //If is primitive, add to returned set
                if(reasonerService.isPrimitive(parent)) {
                    closestPrimitives.add(parent);
                    continue;
                }
                //If not primitive, add to check
                iterator.add(parent);
                iterator.previous();
            }
        }

        return closestPrimitives;
    }

    //type 1 and type 2 GCI subconcepts:
    //      *type 1: classToDefine is subconcept of a sufficiency condition of a GCI concept. Here: replace GCI concept with next proximal primitives
    //      *type 2: classToDefine is subconcept of a necessary condition of a GCI concept. Here: normal authoring form, no change.
    private Set<OWLClass> eliminateSufficientProximalGCIConcepts(OWLClass classToDefine, Set<OWLClass> parentClasses) {
        Set<OWLClass> newProximalPrimitiveParents = new HashSet<>();
        ListIterator<OWLClass> parentIterator = new ArrayList<>(parentClasses).listIterator();

        //for(OWLClass parent:parentClasses) {
        while(parentIterator.hasNext()) {
            OWLClass parent = parentIterator.next();
            //for each parent A, check if occurs in axiom of form C <= A, where C is a complex concept
            if(namer.hasAssociatedGCIs(parent)) {
                System.out.println("Found GCI parent: " + parent + "Checking type of subconcept relationship for class: " + classToDefine);
               //for each associated GCI, check type 1 or type 2 relationship for classToDefine
                boolean isTypeOne = false;
                for(OWLClass gciName:namer.returnNamesOfGCIsForSuperConcept(parent)) {
                    if(reasonerService.getAncestors(classToDefine).contains(gciName)) {
                        //type 1 -- add proximal primitives of GCI concept to parents for classToDefine, replacing GCI concept.
                        System.out.println("Type 1 concept detected: " + classToDefine + " with parent: " + parent);
                        isTypeOne = true;
                        Set<OWLClass> gciProximalPrimitives = computeClosestPrimitiveAncestors(parent);
                        System.out.println("Prox primitive parents for GCI concept: " + parent + " are: " + gciProximalPrimitives);
                        for(OWLClass proximalPrimitive:gciProximalPrimitives) {
                            //newProximalPrimitiveParents.add(proximalPrimitive);
                            parentIterator.add(proximalPrimitive);
                            parentIterator.previous();
                        }
                        break;
                    }
                }
                //type 2 -- retain GCI concept in proximal primitive parent set
                if(!isTypeOne) {
                    System.out.println("Type 2 concept detected: " + classToDefine + " with parent: " + parent);
                    newProximalPrimitiveParents.add(parent);
                    continue;
                }
            }
            else {
                //if not GCI concept, retain proximal primitive parent
                newProximalPrimitiveParents.add(parent);
            }
        }
        return newProximalPrimitiveParents;
    }

}

