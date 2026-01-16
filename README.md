# Enterprise Architect MIM model naar SQL Developer Data Modeler omzetten

Metamodel Informatiemodellering (MIM) modellen zijn veelal met [Sparx Enterprise Architect](https://sparxsystems.com/products/ea/) (EA) in Unified Modeling Language (UML) beschreven. Voor EA is een betaalde licentie nodig en het is alleen voor Windows beschikbaar. Op de [EA downloads pagina](https://sparxsystems.com/products/ea/downloads.html) is echter wel een viewer beschikbaar. Dus het bekijken van de MIM modellen is mogelijk via de viewer.

[Oracle SQL Developer Data Modeler](https://www.oracle.com/database/sqldeveloper/technologies/sql-data-modeler/) (SDDM) is een gratis beschikbaare modelleer tool die op meerdere platformen beschikbaar is. Op de [SDDM download pagina](https://www.oracle.com/database/sqldeveloper/technologies/sql-data-modeler/download/) zijn versies voor Windows, Mac OSX en Linux beschikbaar.

## Groovy in SDDM
Om een EA model in te lezen gaan we de scripting mogelijkheden van SDDM gebruiken. Aangezien SDDM een Java applicatie is en ik ervaring heb met de scriptingtaal Groovy in Java, is het script in Groovy opgezet. Daarvoor moeten we wel eerst Groovy als scripting engine toevoegen aan SDDM.

1. Ga naar de [Groovy download pagina](http://groovy-lang.org/download.html) en kies de laatste versie.
2. Download de "SDK Bundle" em unzip het bestand.
3. Ik heb in de ../datamodeler/lib deze libraries toegevoegd:
    - groovy-jsr223-4.0.27.jar
    - groovy-4.0.27.jar
    - groovy-sql-4.0.27.jar
4. Daarna de datamodeler.conf aangepast en het volgende toegevoegd: 
    ```Add Groovy scripting
    AddJavaLibFile ../lib/groovy-jsr223-4.0.27.jar
    AddJavaLibFile ../lib/groovy-4.0.27.jar
    AddJavaLibFile ../lib/groovy-sql-4.0.27.jar
    ```
5. Herstart SDDM     

Nu is de Groovy scripting taal beschikbaar voor SDDM. 

## De EA repository

Een EA model wordt opgeslagen als een MS Access database voor versies kleiner dan 16. Voor hogere versies is het een sqlite database.
In de blog [Suchen und Finden 2: Enterprise Architect DB-Schema](https://blog.sparxsystems.de/en_GB/ea/ea-features/ea-model-search/enterprise-architect-db-schema/) is het database schema getoond. Via queries op de repository kunnen we de EA model elementen ophalen en omzetten naar de LDM model elementen.
    
Uit de [MIM-Werkomgeving/MetamodelUML](https://github.com/Geonovum/MIM-Werkomgeving/blob/master/MetamodelUML.md) hebben we de tabellen met de MIM-modelelement mappings overgenomen en de SDDM varianten toegevoegd:    

### Kern zonder Metagegevens

| **MIM metaclass** | **Stereotype**      | **Metaclass UML 2.5**            |  **In EA**        | **In SDDM**              
| ----------------- | ------------------- | -------------------------------- |  ---------------- | -----------
| Objecttype        | «Objecttype»        | (UML) Class                      |  Class            | Entity                        
| Attribuutsoort    | «Attribuutsoort»    | (UML) Property                   |  Attribute (1:1)  | Attribute                     
| Attribuutsoort    | «Attribuutsoort»    | (UML) Property                   |  Attribute (1:n)  | Attribute met Collection Type van Data Type 
| Gegevensgroep     | «Gegevensgroep»     | (UML) Property                   |  Attribute        | Attribute met Structured Type van Gegevensgroeptype 
| Gegevensgroeptype | «Gegevensgroeptype» | (UML) Class                      |  Class            | Structured Type   
| Generalisatie     | «Generalisatie»     | (UML) Generalization             |  Generalization   | Inheritance                   
| Relatiesoort      | «Relatiesoort»      | (UML) Association                |  Association      | Relationship                  
| Relatieklasse     | «Relatieklasse»     | (UML) Association én (UML) Class |  Associationclass | Relationship met Attributes  

### Datatypen zonder Metagegevens

_Datatypen_

| **MIM metaclass**       | **Stereotype**          | **Metaclass UML 2.5**    |  **In EA**          | **In SDDM**                
| ----------------------- | ----------------------- | ------------------------ | ------------------ | ----------- 
| Primitief datatype      | «Primitief datatype»    | (UML) Primitive Type     | Datatype           | Logical Data Type of Distinct Type             
| Gestructureerd datatype | «Gestructuurd datatype» | (UML) Datatype           | Datatype           | Structured Type            
| Data-element            | «Data-element»          | (UML) Property           | Attribute          | Attribute van Structured Type               
| Enumeratie              | \-                      | (UML) Enumeration        | Enumeration        | Domain                     
| Enumeratiewaarde        | \-                      | (UML) EnumerationLiteral | EnumerationLiteral | Domain Value 
| Referentielijst         | «Referentielijst»       | (UML) Datatype           | Datatype           | Entity met Relationship naar Entity                    
| Referentie-element      | «Referentie-element»    | (UML) Property           | Attribute          | Attribute                  
| Codelijst               | «Codelijst»             | (UML) Datatype           | Datatype           | Structured Type met Waade-item als Attribuut                    
       
**De modellering van een Keuze**

Er zijn drie metaklassen met de naam Keuze maar elke keer als extensie van een andere UML metaklasse, waar ook uit blijkt om welke variant van de keuze het gaat. 

| **MIM metaclass** | **Stereotype** | **Metaclass UML 2.5** |  **In EA**    | **In SDDM** 
| ----------------- | -------------- | --------------------- |  ------------ | ---------- 
| Keuze             | Keuze          | (UML) Class           |  Class        | Entity           
| Keuze             | Keuze          | (UML) Datatype        |  Datatype     | Structured Type met Hive Data Type UNION           
| Keuze             | Keuze          | (UML) Property        |  Attribute    | Structured Type           


