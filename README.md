# SNOMED CT Subontology Extraction Prototype

Produces an extracted subontology for a given background ontology and set of concepts, with the aim of satisfying the 
following criteria:
- All focus (input) concept definitions are equivalent in the source and sub ontologies.
- The transitive closure between all concepts in the final subontology is equal (up to the signature of the subontology)
  in the source and sub ontologies.

**Author**: Warren Del-Pinto (warren.del-pinto@manchester.ac.uk, warren.delpinto@gmail.com)\

### Process outline
Steps outline:
1) Compute abstract (authoring) form definitions for each focus concept in the input set (including GCI axioms for these concepts)
2) Definition expansion: automatically identify required supporting concept definitions to satisfy the above criteria
3) Populate RBox (currently star module of roles appearing in the definitions added during steps (1) and (2)
4) Addition of top-level SCT groupers (later may expand to utilise grouper selection algorithms)
5) Completion of the transitive closure between all concepts in the subontology, using atomic inclusions A1 <= A2 only where necessary
6) Shrinking of subontology hierarchy (i.e., removal of unnecessary supporting concepts)

When computing the subontology in RF2 format, the following steps are also included:
- Automatic addition of required metadata concepts (for loading into subontology browser, e.g. language concepts)
- Computation of NNF definitions for Relationship RF2 file

## Running the prototype
Currently, the prototype can be run from the RunSubontologyExtraction.java file. In the main class, replace the paths 
with local ones.

The following must be specified:
- Source ontology file (by path, an OWL file)
- Focus concept list (by path, as inputRefsetFile - a text file containing a single column of concept identifiers. 
  Note: preprocessing refset files - can just extract conceptID column and save separately)
- Output path

Two options can also be specified:
- `computeRF2` - computes the RF2 format of the subontology, alongside the default .owl file
- `verifySubontology` - runs verification for the computed subontology to check critera (1) and (2) above.
**WARNING: can be expensive for larger subontologies**

If computing RF2, the following must also be specified:
- `sourceRF2FilePath` - path to the RF2 format associated with the Source ontology OWL file specified above
        
## Compiling RF2 notes
Currently RF2 files are printed as follows:
- **subontologyRF2 folder** contains all required RF2 files aside from the following...
- **Relationship RF2 file** placed inside same directory as above folder, to compile RF2 move this to 
  Snapshot/Terminology subdirectory in subontologyRF2
- **authoring_OWLRefset_RF2** two files should be copied from this folder to the Snapshot/Terminology subdirectory of 
  the subontologyRF2 folder:
  - _**sct2_sRefset_OWLExpressionSnapshot.txt**_ : contains the OWL definitions associated with the subontology
  - _**sct2_TextDefinition_Snapshot.txt**_: contains the associated descriptions etc required for the browser

## Dependency Notes
Printing uses the content-extraction branch as a dependency to convert from OWL-to-RF2 and vice versa.

To print results in FSN format (source type destination), you will need to include the latest SCT description RF2 file
(e.g. sct2_Description_Snapshot-en_INT_20200731.zip) in the following directory: `src/resources`
