# Enterprise Architect MIM model naar SQL Developer Data Modeler omzetten

Metamodel Informatiemodellering (MIM) modellen zijn veelal met [Sparx Enterprise Architect](https://sparxsystems.com/products/ea/) (EA) in Unified Modeling Language (UML) beschreven. Voor EA is een betaalde licentie nodig en het is alleen voor Windows beschikbaar. Op de [EA downloads pagina](https://sparxsystems.com/products/ea/downloads.html) is echter wel een viewer beschikbaar. Dus het bekijken van de MIM modellen is mogelijk via de viewer.

[Oracle SQL Developer Data Modeler](https://www.oracle.com/database/sqldeveloper/technologies/sql-data-modeler/) (SDDM) is een gratis beschikbare modelleer tool die op meerdere platformen beschikbaar is. Op de [SDDM download pagina](https://www.oracle.com/database/sqldeveloper/technologies/sql-data-modeler/download/) zijn versies voor Windows, Mac OSX en Linux beschikbaar. Door de MIM modellen naar SDDM om te zetten is het beschikbaar in een vrije modelleer tool. Een SDDM Design kan verschillende modellen bevatten in een hierarchie:
- Logical model -< Relational model -< Physical model

We zetten het MIM model om naar een LDM, zodat we van hieruit meerdere Relational modellen ten behoeve van de implementatie kunnen aanmaken:
1. modellen voor de structuur
2. modellen voor de flow

Die we dan later uitwerken naar een fysieke imlementatie via scripting templates.

## Groovy in SDDM
Om een EA model in te lezen gaan we de scripting mogelijkheden van SDDM gebruiken. De blog post [Data modeler scripting 101 : Let’s start at the very beginning](https://daveschleis.wordpress.com/2017/08/15/data-modeler-scripting-101-lets-start-at-the-very-beginning/) van Dave Schleis geeft een goede introductie.  Aangezien SDDM een Java applicatie is en ik ervaring heb met de scriptingtaal Groovy in Java, is het script in Groovy opgezet. Daarvoor moeten we wel eerst Groovy als scripting engine toevoegen aan SDDM. Verder hebben we ook de JDBC drivers nodig om de EA repository uit te kunnen lezen. Voor MS Access kunnen we de [UCanAccess JDBC driver](https://ucanaccess.sourceforge.net/site.html) en voor SQLite de [SQLite JDBC Driver](https://sourceforge.net/projects/sqlite-jdbc-driver.mirror/) gebruiken.

1. Ga naar de [Groovy download pagina](http://groovy-lang.org/download.html) en kies de laatste versie.
2. Download de "SDK Bundle" en unzip het bestand.
3. Ik heb in de ../datamodeler/datamodeler/lib deze libraries toegevoegd:
    - groovy-jsr223-5.0.4.jar
    - groovy-5.0.4.jar
    - groovy-sql-5.0.4.jar
4. Daarna de datamodeler.conf aangepast en het volgende toegevoegd: 
    ```
    #Add Groovy scripting
    AddJavaLibFile ../lib/groovy-jsr223-5.0.4.jar
    AddJavaLibFile ../lib/groovy-5.0.4.jar
    AddJavaLibFile ../lib/groovy-sql-5.0.4.jar
    ```
5. Ik heb in ../datamodeler/jdbc/lib folder deze libraries toegevoegd voor UCanAccess:
    - commons-lang3-3.8.1.jar
    - commons-logging-1.2.jar
    - hsqldb-2.5.0.jar
    - jackcess-3.0.1.jar
    - ucanaccess-5.0.1.jar
6. Voor SQLite:
    - sqlite-jdbc-3.51.1.0.jar
7. En ook nog voor [DuckDB](https://repo1.maven.org/maven2/org/duckdb/duckdb_jdbc/1.4.3.0/duckdb_jdbc-1.4.3.0.jar), dat we mogelijk later nog gaan gebruiken:
    - duckdb_jdbc-1.4.3.0.jar  
8. Daarna de datamodeler.conf aangepast en het volgende toegevoegd: 
    ```
    #Add for MS Access driver libs
    AddJavaLibFile ../../jdbc/lib/commons-lang3-3.8.1.jar
    AddJavaLibFile ../../jdbc/lib/commons-logging-1.2.jar
    AddJavaLibFile ../../jdbc/lib/hsqldb-2.5.0.jar
    AddJavaLibFile ../../jdbc/lib/jackcess-3.0.1.jar
    AddJavaLibFile ../../jdbc/lib/ucanaccess-5.0.1.jar
    #Add DuckDB driver
    AddJavaLibFile ../../jdbc/lib/duckdb_jdbc-1.4.3.0.jar
    #Add SQLite driver
    AddJavaLibFile ../../jdbc/lib/sqlite-jdbc-3.51.1.0.jar
    ```
9. Herstart SDDM   

Nu is de Groovy scripting taal beschikbaar voor SDDM en de Groovy SQL module om de EA bestanden te kunnen bevragen. 

## De EA repository

Een EA model wordt opgeslagen als een MS Access database voor versies kleiner dan 16. Voor hogere versies is het een sqlite database.
In de blog [Suchen und Finden 2: Enterprise Architect DB-Schema](https://blog.sparxsystems.de/en_GB/ea/ea-features/ea-model-search/enterprise-architect-db-schema/) is het database schema getoond. Via queries op de repository kunnen we de EA model elementen ophalen en omzetten naar de LDM model elementen. 

We gaan het [Fietsenwinkel MIM 1.2 voorbeeld model](https://github.com/Geonovum/mim-metamodel/werkomgeving/voorbeeldmodel/uml/EA15_Fietsenwinkel(mim1.2).EAP) gebruiken als eerste referentie model, aangezien het alle concepten bevat. Later gaan we het nog testen met ander modellen zoals:
- het model voor de Basisadministratie Adressen en Gebouwen (BAG)
- de MIM variant van het Gemeentelijk Gegevens Model (GGM), een groter model
- ...

    
Uit het [MIM-Werkomgeving/MetamodelUML](https://github.com/Geonovum/MIM-Werkomgeving/blob/master/MetamodelUML.md) hebben we de tabellen met de MIM-modelelement mappings overgenomen en de SDDM varianten toegevoegd:    

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

| **MIM metaclass**       | **Stereotype**          | **Metaclass UML 2.5**    |  **In EA**          | **In SDDM**                
| ----------------------- | ----------------------- | ------------------------ | ------------------ | ----------- 
| Primitief datatype      | «Primitief datatype»    | (UML) Primitive Type     | Datatype           | Logical Data Type of Distinct Type             
| Gestructureerd datatype | «Gestructuurd datatype» | (UML) Datatype           | Datatype           | Structured Type            
| Data-element            | «Data-element»          | (UML) Property           | Attribute          | Attribute van Structured Type               
| Enumeratie              | \-                      | (UML) Enumeration        | Enumeration        | Domain                     
| Enumeratiewaarde        | \-                      | (UML) EnumerationLiteral | EnumerationLiteral | Domain Value 
| Referentielijst         | «Referentielijst»       | (UML) Datatype           | Datatype           | Structured Type                    
| Referentie-element      | «Referentie-element»    | (UML) Property           | Attribute          | Attribute                  
| Codelijst               | «Codelijst»             | (UML) Datatype           | Datatype           | Structured Type met Waarde-item als Attribuut                    
       
**De modellering van een Keuze**

Er zijn drie metaklassen met de naam Keuze maar elke keer als extensie van een andere UML metaklasse, waar ook uit blijkt om welke variant van de keuze het gaat. 

| **MIM metaclass** | **Stereotype** | **Metaclass UML 2.5** |  **In EA**    | **In SDDM** 
| ----------------- | -------------- | --------------------- |  ------------ | ---------- 
| Keuze             | Keuze          | (UML) Class           |  Class        | Entity           
| Keuze             | Keuze          | (UML) Datatype        |  Datatype     | Structured Type met Hive Data Type UNION           
| Keuze             | Keuze          | (UML) Property        |  Attribute    | Structured Type           


