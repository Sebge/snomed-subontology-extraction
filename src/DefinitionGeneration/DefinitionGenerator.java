package DefinitionGeneration;

import Classification.OntologyReasoningService;
import NamingApproach.PropertyValueNamer;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.util.*;

public abstract class DefinitionGenerator {

    protected OWLOntology backgroundOntology;
    protected OntologyReasoningService reasonerService;
    private PropertyValueNamer namer;
    private OWLOntologyManager man;
    protected OWLDataFactory df;
    protected List<OWLAxiom> generatedDefinitions;
    protected Set<OWLClassExpression> latestNecessaryConditions;
    protected Set<OWLAxiom> undefinedClasses;

    public DefinitionGenerator(OWLOntology inputOntology, OntologyReasoningService reasonerService, PropertyValueNamer namer) {
        backgroundOntology = inputOntology;
        this.reasonerService = reasonerService;
        this.namer = namer;
        man = OWLManager.createOWLOntologyManager();
        df = man.getOWLDataFactory();
        //subPropertiesOfreflexiveProperties = new ArrayList<OWLObjectPropertyExpression>();
        generatedDefinitions = new ArrayList<OWLAxiom>();
        undefinedClasses = new HashSet<OWLAxiom>(); //TODO: add "memory" of generated defs somewhere?
    }

    public abstract void generateDefinition(OWLClass cls, Set<RedundancyOptions> redundancyOptions);

    public Set<OWLClass> reduceClassSet(Set<OWLClass> inputClassSet) {
        inputClassSet = reasonerService.reduceClassSet(inputClassSet);
        return (inputClassSet); //TODO: return as list or set?
    }

    //TODO: max nesting is RG(R some C ...) correct? If so, no "depth" parameter needed here.
    public Set<OWLObjectSomeValuesFrom> eliminateRoleGroupRedundancies(Set<OWLObjectSomeValuesFrom> inputPVs) {
        Set<OWLObjectSomeValuesFrom> reducedInputPVs = new HashSet<OWLObjectSomeValuesFrom>();

        for(OWLObjectSomeValuesFrom pv:inputPVs) {
            if(pv.getFiller() instanceof OWLObjectIntersectionOf) {
                Set<OWLObjectSomeValuesFrom> pvFillers = new HashSet<OWLObjectSomeValuesFrom>();
                for(OWLClassExpression filler:pv.getFiller().asConjunctSet()) { //TODO: note, unnecessary if we know all role groups only contain pvs
                    if(filler instanceof OWLObjectSomeValuesFrom) {
                        pvFillers.add((OWLObjectSomeValuesFrom) filler);
                    }
                    else {
                        System.out.println("NAMED CLASS FOUND IN ROLE GROUP!");
                    }
                }

                Set<OWLClass> fillerNames = replacePVsWithNames(pvFillers);
                Set<OWLObjectSomeValuesFrom> reducedFillers = replaceNamesWithPVs(reduceClassSet(replacePVsWithNames(pvFillers)));

                reducedInputPVs.add(df.getOWLObjectSomeValuesFrom(pv.getProperty(), df.getOWLObjectIntersectionOf(reducedFillers)));
                continue;
            }
            //if not a role group
            reducedInputPVs.add(pv);
        }
        return reducedInputPVs;
    }

    //TODO: use of this?
    /*
    public void computeSubPropertiesOfReflexiveProperties() {
        System.out.println("Computing reflexive properties.");
        EntitySearcher searcher = new EntitySearcher();

        //get declared reflexive properties
        for(OWLObjectProperty prop:backgroundOntology.getObjectPropertiesInSignature()) {
            if(searcher.isReflexive(prop, backgroundOntology)) {
                subPropertiesOfreflexiveProperties.add(prop);
            }
        }
        ListIterator<OWLObjectPropertyExpression> reflexiveIterator = subPropertiesOfreflexiveProperties.listIterator();
        List<OWLObjectPropertyExpression> additionalReflexiveProperties = new ArrayList<OWLObjectPropertyExpression>();

        //recursively add all subproperties of reflexive properties as reflexive (ELK does not support finding superproperties)
        while(reflexiveIterator.hasNext()) {
            OWLObjectPropertyExpression prop = reflexiveIterator.next();
            System.out.println("Current prop: " + prop.toString());
            for(OWLSubObjectPropertyOfAxiom propAx:backgroundOntology.getObjectSubPropertyAxiomsForSuperProperty(prop)) {
                System.out.println("Propax: " + propAx.toString());
                if(!additionalReflexiveProperties.contains(propAx.getSubProperty())) {
                    reflexiveIterator.add(propAx.getSubProperty());
                    reflexiveIterator.previous();
                    additionalReflexiveProperties.add(propAx.getSubProperty());
                    System.out.println("Reflexive property added: " + propAx.getSubProperty().toString());
                }
            }
        }
        subPropertiesOfreflexiveProperties.addAll(additionalReflexiveProperties);
    }
     */

    Set<OWLObjectSomeValuesFrom> eliminateReflexivePVRedundancies(Set<OWLObjectSomeValuesFrom> inputPVs, OWLClass inputClass) {
        Set<OWLObjectSomeValuesFrom> reducedInputPVs = new HashSet<OWLObjectSomeValuesFrom>();

        //retrieve reflexive PVs in definition
        for(OWLObjectSomeValuesFrom pv:inputPVs) {
            boolean isReflexiveProperty = checkIfReflexiveProperty(pv.getProperty().asOWLObjectProperty());
            if (isReflexiveProperty) {
                if(reasonerService.getAncestorClasses(inputClass).contains(pv.getFiller()) || pv.getFiller().equals(inputClass)) {
                    //System.out.println("Is reflexive redundancy: " + pv);
                    continue;
                }
            }
            reducedInputPVs.add(pv);
        }
        return reducedInputPVs;
    }

    public boolean checkIfReflexiveProperty(OWLObjectPropertyExpression r) {
        EntitySearcher searcher = new EntitySearcher();
        if(searcher.isReflexive(r, backgroundOntology)) {
            //System.out.println("Property: " + r.toString() + " is reflexive.");
            return true;
        }
        return false;
    }

    //TODO: refactor, some of these bit redundant with renamer.
    public Set<OWLClass> extractNamedPVs(Set<OWLClass> classes) {
        Set<OWLClass> renamedPVs = new HashSet<OWLClass>();

        for (OWLClass cls : classes) {
            if (namer.isNamedPV(cls)) {
                renamedPVs.add(cls);
            }
        }
        return renamedPVs;
    }

    public Set<OWLClass> extractNamedClasses(Set<OWLClass> classes) {
        Set<OWLClass> namedClasses = new HashSet<OWLClass>();

        for (OWLClass cls : classes) {
            if (!namer.isNamedPV(cls)) {
                namedClasses.add(cls);
            }
        }
        return namedClasses;
    }

    public Set<OWLObjectSomeValuesFrom> replaceNamesWithPVs(Set<OWLClass> classes) {
        Set<OWLObjectSomeValuesFrom> pvs = namer.retrievePVsFromNames(classes);
        return pvs;
    }

    public Set<OWLClass> replacePVsWithNames(Set<OWLObjectSomeValuesFrom> pvs) {
        return namer.retrieveNamesForPVs(pvs);
    }

    protected void constructDefinitionAxiom(OWLClass definedClass, Set<OWLClassExpression> definingConditions) {
        definingConditions.remove(df.getOWLThing());
        definingConditions.remove(df.getOWLNothing());
        if (definingConditions.size() == 0) {
            System.out.println("Undefined class: " + definedClass);
            undefinedClasses.add(df.getOWLSubClassOfAxiom(df.getOWLThing(), definedClass));
            return;
        }
        else if(definingConditions.size() == 1) {
            OWLClassExpression definingCondition = (new ArrayList<OWLClassExpression>(definingConditions)).get(0);
            if(!backgroundOntology.getEquivalentClassesAxioms(definedClass).isEmpty()) {
                //System.out.println("Equivalent class axiom for class: " + definedClass);
                generatedDefinitions.add(df.getOWLEquivalentClassesAxiom(definedClass, definingCondition));
            }
            else {
                //System.out.println("Necessary class axiom for class: " + definedClass);
                generatedDefinitions.add(df.getOWLSubClassOfAxiom(definedClass, definingCondition));
            }
            return;
        }
        if(!backgroundOntology.getEquivalentClassesAxioms(definedClass).isEmpty()) {
            generatedDefinitions.add(df.getOWLEquivalentClassesAxiom(definedClass, df.getOWLObjectIntersectionOf(definingConditions)));
        }
        else {
            generatedDefinitions.add(df.getOWLSubClassOfAxiom(definedClass, df.getOWLObjectIntersectionOf(definingConditions)));
        }
        latestNecessaryConditions = definingConditions;
    }

    public List<OWLAxiom> getGeneratedDefinitions() {
        return this.generatedDefinitions;
    }

    public OWLAxiom getLastDefinitionGenerated() {
        OWLAxiom latestDefinition = null;
        if (generatedDefinitions != null && !generatedDefinitions.isEmpty()) {
             latestDefinition = generatedDefinitions.get(generatedDefinitions.size()-1);
        }
        return latestDefinition;
    }

    public Set<OWLClassExpression> getLatestNecessaryConditions() {
        return this.latestNecessaryConditions;
    }

    public Set<OWLAxiom> getUndefinedClassAxioms() { return this.undefinedClasses; }
}
