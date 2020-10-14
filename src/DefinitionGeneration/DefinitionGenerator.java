package DefinitionGeneration;

import Classification.OntologyReasoningService;
import RenamingApproach.PropertyValueRenamer;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public abstract class DefinitionGenerator {

    protected OWLOntology backgroundOntology;
    protected OntologyReasoningService reasonerService;
    private PropertyValueRenamer renamer;
    private OWLOntologyManager man;
    private OWLDataFactory df;
    protected Set<OWLAxiom> generatedDefinitions;
    protected Set<OWLAxiom> undefinedClasses;

    public DefinitionGenerator(OWLOntology inputOntology, OntologyReasoningService reasonerService, PropertyValueRenamer renamer) {
        backgroundOntology = inputOntology;
        this.reasonerService = reasonerService; //TODO: make better. Null pointer exception if "this" excluded.
        this.renamer = renamer;
        man = OWLManager.createOWLOntologyManager();
        df = man.getOWLDataFactory();
        generatedDefinitions = new HashSet<OWLAxiom>();
        undefinedClasses = new HashSet<OWLAxiom>(); //TODO: add "memory" of generated defs somewhere?
    }

    public abstract void generateDefinition(OWLClass cls);


    public Set<OWLClass> reduceClassSet(Set<OWLClass> inputClassSet) {
        Set<OWLClass> redundantClasses = new HashSet<OWLClass>();

        Set<OWLClass> otherClasses = new HashSet<OWLClass>(inputClassSet);
        for (OWLClass cls : inputClassSet) {
            otherClasses.remove(cls);
            if (reasonerService.atLeastOneStrongerThan(cls, otherClasses)) {
                redundantClasses.add(cls);
            }
            otherClasses.add(cls); //retain redundancies to check against (?)
            // TODO: check, would be problematic if we have equivalent named classes or PVs, since this will mean both are removed. Is this ever the case with SCT?
        }                           // TODO:...but if A |= B, then we have B |= C, via this approach we can safely remove them as we identify them? DOUBLE CHECK.

        /* Doing checks one by one
        List<OWLClass> inputClassesList = new ArrayList<OWLClass>(inputClassSet);
        for(int i=0; i<inputClassesList.size(); i++) {
            OWLClass currentCls = inputClassesList.get(i);
            for(int j=i+1; j<inputClassesList.size(); i++) {
                OWLClass comparingCls = inputClassesList.get(j);

                if(reasonerService.isStrongerThan(currentCls, comparingCls)) {
                    redundantClasses.add(currentCls);
                    break;
                }
            }

        }
         */

        inputClassSet.removeAll(redundantClasses);
        inputClassSet.remove(df.getOWLThing());
        inputClassSet.remove(df.getOWLNothing());
        return (inputClassSet); //TODO: return as list or set?
    }

    public Set<OWLClass> extractRenamedPVs(Set<OWLClass> classes) {
        Set<OWLClass> renamedPVs = new HashSet<OWLClass>();

        for (OWLClass cls : classes) {
            if (renamer.isRenamedPV(cls) == true) {
                renamedPVs.add(cls);
            }
        }
        return renamedPVs;
    }

    public Set<OWLClass> extractNamedClasses(Set<OWLClass> classes) {
        Set<OWLClass> namedClasses = new HashSet<OWLClass>();

        for (OWLClass cls : classes) {
            if (renamer.isRenamedPV(cls) == false) {
                namedClasses.add(cls);
            }
        }
        return namedClasses;
    }

    public Set<OWLObjectSomeValuesFrom> replaceNamesWithPVs(Set<OWLClass> classes) {
        Set<OWLObjectSomeValuesFrom> pvs = renamer.retrievePVsFromNames(classes);
        return pvs;
    }

    //TODO: refactor, bit redundant with renamer.
    public Set<OWLClass> retrieveNamesForPVs(Set<OWLObjectSomeValuesFrom> pvs) {
        return renamer.retrieveNamesForPVs(pvs);
    }

    protected void constructNecessaryDefinitionAxiom(OWLClass definedClass, Set<OWLClassExpression> definingConditions) {
        //Optional<OWLAxiom> definition = null;
        System.out.println("cls: " + definedClass);
        System.out.println("definingConditions: " + definingConditions);
        if (definingConditions.size() == 0) {
            System.out.println("No necessary conditions for class: " + definedClass);
            //no necessary condition for class (it is "top level class". Return top <= class. TODO: better way?
            undefinedClasses.add(df.getOWLSubClassOfAxiom(df.getOWLThing(), definedClass));
            return;
        }
        //TODO: handle case with 1 necessary condition, no conjunction in superclass?
        generatedDefinitions.add(df.getOWLSubClassOfAxiom(definedClass, df.getOWLObjectIntersectionOf(definingConditions)));
    }

    public Set<OWLAxiom> getGeneratedDefinitions() {
        return this.generatedDefinitions;
    }

    public Set<OWLAxiom> getUndefinedClassAxioms() { return this.undefinedClasses; }
}
