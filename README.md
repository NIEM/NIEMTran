# Building

    mvn package appassembler:assemble

# Quick Start

You will find the executable in `target/appassembler/bin`. <br>
There is a sample IEPD in `target/appassembler/share/sample-iepd`. <br>
Try the following commands in the IEPD directory:


    niemtool check xml-catalog.xml extension/CrashDriver.xsd
    niemtool check -v xml-catalog.xml extension/CrashDriver.xsd
    niemtool compile xml-catalog.xml extension/CrashDriver.xsd
    niemtool translate CrashDriver.no iep/iep1.xml > iep1.json
    niemtool translate --x2t CrashDriver.no iep/iep1.xml > iep1.ttl
    niemtool translate --x2g CrashDriver.no iep/iep1.xml > iep1.gv
    dot -Tpng iep1.gv > iep1.png

# Overview

This is a project for translating data between NIEM XML, NIEM JSON,
and NIEM RDF, a way to convert a NIEM message in one serialization
into the equivalent NIEM message in either of the others. It is a
lossless translation, preserving all of the meaning that is defined by
the XML schema and the NIEM Naming and Design Rules (NDR). This translation is 
guided only by the information in the XML schema of the message description.

What's finished now: the XML to JSON translator. Because NIEM JSON is
based on JSON-LD, we get the XML to RDF translator for free. The
translators from NIEM JSON and NIEM RDF are not yet implemented.

As the genie says, there are also "a few provisos, a couple of quid
pro quos"s for the XML to JSON translation, concerning external and
wildcard schema components. These are described in a later section.

# The Tools

The project comprises three stand-alone command-line developer tools,
written in Java, and based on the Xerces2 Java Parser. 

* A NIEM XML schema assembly diagnostic tool, which finds schema
  assembly ambiguities in an XML schema document pile
  
* A NIEM XML schema compiler, which converts an XML schema from a NIEM
  message description into an object file telling the translator how
  to handle the NIEM message formats defined by that schema

* A NIEM XML-to-JSON translator, which uses the object file to convert
  XML documents that conform to those message formats into the
  equivalent NIEM JSON. 
  
Some of the Java classes in the project are only suitable for a
development environment, while others can be reused and deployed to
translate NIEM XML at runtime.

## Schema Check

The schema check program is not required for NIEM translation -- but
can help the developer ensure the schema he creates is the schema he
intends.

Conformance of a NIEM XML message is defined in part by XML Schema
validation; that is, XML documents which do not validate against the
XML schema for a NIEM message format are not valid instance messages
of that format.

While NIEM conformance is defined in terms of an XML *schema*, a NIEM
message description can only provide a collection of XML *schema
documents*. Schema assembly, the process of constructing an XML schema
from schema documents, is underspecified in the W3C
recommendation. Because NIEM does not yet have a schema assembly
specification of its own, NIEM conformance in effect depends on the
behavior of the chosen XML validating parser. Alas, there is more than
one such parser, and they do not all do exactly the same thing, and
they do not always do what the developer expects.

The schema check program uses SAX to parse a set of schema documents
specified in a NIEM message description and reports any ambiguities
which could cause different validating parser to construct different
schemas. It also reports various NIEM style concerns that the
developer may want to correct. Finally, it uses the Xerces parser to
validate the schema document set, and reports the schema documents
actually loaded during validation.

The schema document set is specified by command-line arguments, which
provide

* Optionally, a list of XML Catalog document file paths.
* A list of initial schema documents and schema namespace URIs
   
The schema thus specified is the schema constructed by:

* Loading each initial schema document, and

* Resolving each initial namespace URI and loading the resulting local
  file, and
  
* Recursively loading the local resource described by every `import`,
  `include`, and `redefine` element encountered, while
  
* Using the catalog resolver constructed from the list of XML Catalog
  documents to resolve each `@namespace` and `@schemaLocation`
  attribute.

The schema check program reports the following errors and warnings:

1. Initial schema document files that cannot be read
1. Initial namespace URIs that cannot be resolved to a readable file
1. Catalog documents that are not valid XML Catalog documents
   (including subordinate catalogs)
1. A namespace URI that resolves to a non-local resource
1. A schemaLocation URI resolves to a non-local resource
1. A schema document load (`import`, `include`, or `redefine`) in
   which the resolved `@namespace` is not the same as the resolved
  `@schemaLocation`
1. A load where the schema document to be loaded can't be determined
1. A load where the schema document can't be parsed
1. A load where the schema document file can't be read
1. A load where the schema document target namespace is not the
   expected namespace
1. A load for a namespace already loaded from a different file
1. A load where there is no catalog entry for the namespace URI (if
   catalog documents have been provided)
1. An `import` with no `@namespace` attribute
1. A load with no `@schemaLocation` attribute
1. An `include` element found in a document with a target namespace
   that has a catalog entry
1. Any errors or warnings returned by Xerces
1. Any namespace prefix bound to more than one namespace URI
1. Any namespace URI bound to more than one prefix
1. Any non-standard prefix bound to a NIEM namespace

The schema check program also reports a list of:

1. Each external namespace (those without a NIEM conformance
   assertion)
1. Each namespace constructed, with a list of documents contributing
   to its content
1. Result of each XML Catalog resolution performed during schema assembly


## Schema Compilation

The schema compiler processes a schema document set specified by the
same command-line arguments as the schema check program:

* Optionally, a list of XML Catalog document file paths.
* A list of initial schema documents and schema namespace URIs

It uses the Xerces XML Schema API to process the schema, producing a
"NIEM object" file containing the schema information required for NIEM
message translation. The object file drives translation of any message
format defined by the input schema.  It consists of:

* The base type of each attribute (string, token, list/token, etc.)

* The base type of each simple-content element

* For each complex-content element, the ordered list of possible child
  elements (not implemented yet)
  
* A unique prefix for each namespace URI in the schema, where no
  prefix is bound to two URIs and no URI is bound to two prefixes.
  
* A list of all external namespaces (defined in schemas having no NIEM
  conformance assertion).

* A flag indicating whether the schema contains any wildcard elements
  or attributes.  (If there are none, then the JSON-LD context is
  fully determined by the XML schema.)
  
The URI-to-prefix bindings generated by the schema compiler determine
the JSON-LD context for messages translated with this NIEM object
file.  When there is more than one binding for a prefix in the schema
document set, preference is given

* First, to namespace declarations in the extension schemas, on the
  assumption the designer knows what he wants
  
* Second, to the standard prefixes found in the NIEM model namespaces
  ("nc" for niem core, etc.)
  
* Lastly, to namespace declarartions in external schemas.

Except for components matching wildcards, the namespace declarations
in the runtime message document may be ignored. The prefixes used in
the XML schema determine the context mappings, not those used in the
runtime message.

## Message Translation

The message translator loads a single NIEM object file, then uses SAX
to parse and process any number of NIEM XML messages with a message
format described by that object file.  Each message is translated into
the equivalent NIEM JSON-LD. Some components in the XML document
receive special handling:

1. `@uri` from any structures namespace becomes the `@id` JSON
   key. The attribute value is the value of `@id`, a relative URI

1. `@ref` and `@id` from any structures namespace becomes the @id`
   key.
   
1. `@metadata` from any structures namespace becomes a reference to
   the metadata object. For example, when there is an element 
   `<ns:JusticeMetadata structures:ref="foo">`, then the attribute
   `structures:metadata="foo"` becomes `"structures:metadata": {
   "@id" : "foo" }`
   
1. Attributes from the XSI namespace are ignored

1. An augmentation element is replaced by its children. Attributes of
   an augmentation element are ignored. (At present, augmentation elements
   are identified by element name; perhaps this should look for 
   derivation from `structures:AugmentationType` instead.)
   
1. Special handing for `@xml:lang`, `@xml:space`, and
   `@relationshipMetadata` in any structures namespace is not yet
   implemented.

The content of elements defined in external schemas is dropped,
unless:

* The NIEM object file provides the name of a class that can process
  elements from the external namespace
  
* The NIEM object file declares that elements from the external schema
  should be processed as if NIEM conforming.
  
For each message, the translator returns a status indicator that may
contain either or both of two flags:

* `X2J_OMITTED` -- XML elements from an external namespace have been
  omitted from the JSON output
  
* `X2J_EXTENDED` -- the usual context for this message format has been
  extended with context mappings for namespaces not found in the
  message description. This can only happen with wildcard elements or
  nonconforming message documents.
  

# Assumptions and Provisos

1. The JSON-to-XML translation is not implemented yet, and the schema
   compiler does not generate the information needed for this
   translation. 

1. `xml:base`, `xml:lang` and `xml:space` attributes are not handled yet.

1. Augmentation element attributes are ignored. Perhaps they should be
   forbidden? Their RDF interpretation is unclear.

1. All `xsi` attributes in XML are ignored when translating to
   JSON. I'm not yet sure what to do about `xsi:type` attributes when
   translating from JSON to XML.

1. Very little care is taken to produce correct output for a document
   that does not conform to the message description schema.

1. Every namespace with a URI beginning with
   http://release.niem.gov/niem/structures/ is a structures
   namespace. These namespaces do not appear in the message format
   context. Attributes named `uri`, `id`, `ref`, and `metadata` in
   these namespaces receive special handling (and do not appear in the
   JSON output).
   
1. Every namespace with a URI beginning with
   http://release.niem.gov/niem/appinfo/ is an appinfo
   namespace. These namespaces do not appear in the message format
   context.
   
1. Every namespace with a URI beginning with
   http://release.niem.gov/niem/proxy/xsd/ is a proxy
   namespace. These namespaces do not appear in the message format
   context.
   
1. Every namespace with a URI beginning with
   http://release.niem.gov/niem/conformanceTargets/ is a conformance
   target namespace. These namespaces do not appear in the message
   format context. An attribute named `conformanceTargets` in such a
   namespace is taken to be a conformance assertion. The first token
   in the attribute value that matches the pattern
   `http://reference.niem.gov/niem/specification/naming-and-design-rules/[^/]*/` 
   defines the NIEM version of the current schema document, with the
   characters matching `[^/]*` taken to be the version number.
   
1. Every namespace with a URI beginning with
   http://release.niem.gov/niem/ not mentioned above is a NIEM model
   namespace. These namespaces are preferred over external namespaces
   when namespace prefixes are assigned.
   
