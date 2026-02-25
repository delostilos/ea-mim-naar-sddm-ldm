// De groovy SQL module om de Enterprise Architect database bestanden te kunnen bevragen
import groovy.sql.Sql

// Swing GUI elementen voor bestands selectie
import javax.swing.filechooser.FileFilter
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JFrame
// Color om straks de kleuren van de ClassificationTypes aan te geven
import java.awt.Color

// functie om eenvoudiger te loggen
def log = { msg -> oracle.dbtools.crest.swingui.ApplicationView.log(msg)}

/*
 
-- MIM refentie model met veel varianten --
Fietsenwinkel MIM 1.2 model https://github.com/Geonovum/mim-metamodel/werkomgeving/voorbeeldmodel/uml/EA15_Fietsenwinkel(mim1.2).EAP 

-- MIM imbag concept
https://github.com/Geonovum/IMBAG/blob/master/concept%20catalogus/semantisch%20gegevensmodel/20161104_IMBAG_UML_concept.EAP
en in MIM 1.1 concept versie.
https://github.com/imbag/catalogus-1punt1-concept/blob/master/imbag.eap

-- integratie model, straks handig om te verglijken met SQL select-from-where + union (set operatie) gebaseerde integratie.
IMX-Geo MIM model          https://github.com/Geonovum/IMX-Geo/ea/imx-geo.eapx

IMX-lineage Model  https://github.com/Geonovum/IMX-LineageModel/blob/main/im/imli202312.EAP

-- Het Informatiemodel Ruimtelijke Ordening
https://github.com/Geonovum/imro/blob/master/informatiemodel/2012/imro2012_1.1.0.EAP

-- IM Geluid
https://github.com/Geonovum/IMG/blob/gh-pages/uml/3.1.1/IMGeluid_v3_1_1.eapx

-- IM Geo
https://github.com/Geonovum/IMGeo/blob/master/informatiemodel/2.2/IMGeo.EAP

-- IMKL - Dataspecificatie voor Utiliteitsnetten.
https://github.com/Geonovum/imkl/blob/master/informatiemodel/3.0.0/imkl_v3.0.0-vastgesteld-20250618.eap.zip

-- geen MIM maar bevat soortgelijke concepten --
Gemeentelijk GegevensModel https://github.com/Gemeente-Delft/Gemeentelijk-Gegevensmodel/blob/master/v2.5.0/gemeentelijk%20gegevensmodel%20EA16.qea
wel MIM, maar draft        https://github.com/Gemeente-Delft/Gemeentelijk-Gegevensmodel/blob/master/v2.5.0/Gemeentelijk%20Gegevensmodel-MIM-draft.qea

*/

// Functie om een melding te geven dat we geen correct bestand hebben geselecteerd.
def geenCorrectBestandMelding = {
	// geen geschikt formaat melding
	def frame = new JFrame("EA to SDDM")
	def optionPane = new JOptionPane()
	def melding = "Geen correct bestand geselecteerd!"
	optionPane.showMessageDialog(frame , melding)
	log melding
}

// de bestandkiezer met als filter de EA bestands extensies.
def openFileDialog = new JFileChooser( dialogTitle: "Kies een EA bestand"
                                     , fileSelectionMode: JFileChooser.FILES_ONLY 
                                     // het file filter moet ook folders accepteren, om deze te kunnen selecteren!
                                     , fileFilter: [ getDescription: "Enterprise Architect bestanden (*.qea of *.eap)"
                                                   , accept:{file -> file ==~ /.*?\.qea|.*?\.eap|.*?\.eapx|.*?\.EAP|.*?\.EAPX|.*?\.QEA/ || file.isDirectory() }
                                                   ] as FileFilter
                                     )
openFileDialog.showOpenDialog()
def file = openFileDialog.getSelectedFile()

// Geen bestand , we stoppen het script
if (!file) {
	geenCorrectBestandMelding()
	return 0
}

log "$file geopend"

// de extensie bepalen om straks de juiste SQL driver op te halen
def file_path = file.toString() 
// het laatste deel van het pad bevat de bestandsnaam
def file_name =  file.toPath().collect()*.toString().last() 
// Maken van de '.' delimited string een array
def file_path_list = file_name.tokenize('.')
// de extensie is het laatste element in de array
def ea_type = file_path_list.last()
// Het design krijgt dezelfde naam als het bestand zonder extensie
def design_name = file_name.replace(".${ea_type}",'')

def url, driver
// bepaal aan de hand van de bestands extensie welke SQL variant we moeten gebruiken. EA versie >= EA16 dan SQLite (qea)
switch(ea_type.toLowerCase()) {
	// de qea extensie geeft aan dat het een sqlite bestand is
	case 'qea':
		url = "jdbc:sqlite:${file_path}"
	     driver = 'org.sqlite.JDBC'
	break
	// de eap of eapx extensie geeft aan dat we het 'oude' msaccess formaat hebben
	case 'eap':
     case 'eapx':
		url = "jdbc:ucanaccess://${file_path}"
	     driver = 'net.ucanaccess.jdbc.UcanaccessDriver' 
     break
     	// geen correcte extensie waar we iets mee kunnen dus stoppen.
     default:
	 	geenCorrectBestandMelding()
		return 0     
	break  
}

// open de database van het EA model
def sql = Sql.newInstance(url, '', '', driver)

// het EA Class model wordt een Logical data model in SDDM
// we nemen de root package 'Model' als root van het EA model
def package_query = """
select *
  from t_package
 where parent_id = 0    
"""
// We nemen hier de eerste rij, aannemend dat er 1 root node is!
def Root = sql.firstRow(package_query)
// map het Logical Model
def design = model.design
design.name = design_name 
design.comment = Root.name
design.objectID = Root.ea_guid
design.setProperty('Id',Root.package_id.toString())

// functie om classificatie type toe te voegen. Input parameters: naam en kleur object
def createClassification = { name, color ->
	def cType = model.design.designLevelSettings.classificationTypeList.find{ ct -> ct.typeName == name}
	if (!cType) {
		cType = new oracle.dbtools.crest.model.ClassificationType(name, color, new Color(0, 0, 0),'')
		model.design.designLevelSettings.classificationTypeList.add(cType)
	}
	return cType
}

log "Design $design_name aangemaakt"

// Class stereotype naar Entity ClassificationType omzetten: 'Objecttype','Gegevensgroeptype','Referentielijst','Keuze','Codelijst'
// Eerst de classificatie typen toevoegen aan het design
def abstractObjectTypeCT = createClassification('Abstract Objecttype', new Color(255, 255, 255)) // wit
def objectTypeCT = createClassification('Objecttype', new Color(204, 255, 255)) // licht blauw
def gegevensgroepTypeCT = createClassification('Gegevensgroeptype', new Color(204, 204, 255)) //licht paars
def referentieLijstCT = createClassification('Referentielijst', new Color(255, 204, 204)) // licht roze
def keuzeCT = createClassification('Keuze', new Color(255, 255, 204)) // licht geel
def codeLijstCT = createClassification('Codelijst', new Color(204, 255, 204)) // licht groen
def multiValueCT = createClassification('MultiValue', new Color(228, 228, 228)) // licht grijs
design.setDesignLevelSettingsChanged(true)


//log 'Design methods'
//log model.design.designLevelSettings.getClassificationTypeByName('Objecttype').typeName

//log design.designLevelSettings.getProperties().keySet()

//def properties = design.designLevelSettings.getProperties().keySet() // collect{ it.name}
//properties.each{p->
	//log p
//}
//log 'einde methods'
//log design.getDLSettingsObject()



// generieke t_attribute query, met als parameter de t_object rij
t_attribute_query = {object_id ->
	return """
	select a.* 
	     , coalesce(cast(replace(tal.value,'1..','') AS integer), a.Length) as lengte
	     , tap.value as formeel_patroon
	     , case when x.xrefid is null then 0 else 1 end as is_id
	     , x.xrefid as id_ea_guid 
	  from t_attribute AS a
	  /* haal de eventuele lengte tag op */ 
	  left
	  join t_attributetag AS tal 
	    on a.ID = tal.elementId
	   and tal.property IN ('Lengte','length')
	  /* haal het formele patroon op indien aanwezig */ 
	  left
	  join t_attributetag AS tap 
	    on a.ID = tap.elementId
	   and tap.property IN ('Formeel patroon')
	  /* haal de eventuele isID ref op tbv key bepaling */ 
	  left
	  join t_xref AS x
	    on x.Client = a.ea_guid 
	   and Description = '@PROP=@NAME=isID@ENDNAME;@TYPE=Boolean@ENDTYPE;@VALU=1@ENDVALU;@PRMT=@ENDPRMT;@ENDPROP;'         
	 where a.object_id = $object_id
	 order 
	    by a.pos
	     , a.id  
	"""	
}
// functie om uit de t_attribute rij de dataType info op te halen.
attributeToLogicalType = { attr ->
	// map EA MIM/GGM naar SDDM logical types
	def eaToLogical = ['A':'String'
	                  ,'AN':'String'
	                  ,'DATUM':'Date'
	                  ,'TIJD':'Time'
	                  ,'DATUMTIJD':'DateTime'
	                  ,'CharacterString':'String'
	                  ,'N':'Decimal'
	                  ,'Number':'Decimal'
	                  ,'int':'Integer'
	                  ,'Surface':'SDO_GEOMETRY'
	                  //,'IBAN':'String'
	                  ,'YEAR':'Sting'
	                  ]
     // probeer uit het formele patroon de lengte af te leiden
     def formeleLengte = 0            
	if (attr.formeel_patroon) {
		// we halen de delen tussen de { en } op voor de lengtes via een regex
		def formeelPatroonList = (attr.formeel_patroon.toString() =~ /(?<=\{)[^}]+(?=\})/ )   
		log attr.formeel_patroon.toString()
		// we gaan de getallen optellen tot een totaal
		formeelPatroonList.each{ fp ->
			//log "p $fp"
			// 0,<max lengte> en 1,<max lengte> komen ook voor, dus strippen we het eerste deel om de max lengte te verkrijgen
			def fl = fp.replace('0,','').replace('1,','')
			formeleLengte += fl.toInteger()
		}
     }
     log "formeleLengte: $formeleLengte"
	// regex om de datatype string te splitsen AN10,1 wordt AN 10 1
     def dataType = (attr.type.toString() =~ /([a-zA-Z_]*)(\d*),?(\d*)/ ) 
     def dataTypeString = dataType[0][1].toString()
     log "$dataTypeString precisionOrSize: $attr.lengte , $attr.precision or ${dataType[0][2]} scale: $attr.scale or ${dataType[0][3]}"
     //log "$dataTypeString precisionOrSize: $attr.lengte , $attr.precision or ${(dataType[0][2])?:'0'} scale: $attr.scale or ${(dataType[0][3])?:'0'}"
     String dataTypeSize 
     int dataTypePrecision
     int dataTypeScale 
     // haal het bijpassende LDM type op, indien niet gevonden neem het EA data type over
     def logicalDataTypeString = eaToLogical[dataTypeString] ?: attr.type.toString()
     def logicalDatatype = design.getLogicalDatatypeSet().getLogTypeByName(logicalDataTypeString) 
     def use = 1 // logical data type
     // Als we geen logisch type kunnen vinden, kijken we of we een domain type kunnen vinden op basis van de classifier id dat wijst naar het object id van het domein.
     if (!logicalDatatype) {
     	logicalDatatype = model.design.defaultDomainsList.find{ dom -> dom.getProperty('Id') == attr.classifier.toString()} 
     	use = 0
     }
     // Als we geen domein type kunnen vinden, kijken we of we een distinct type kunnen vinden op basis van de naam van het distinct type.
     if (!logicalDatatype) {
     	logicalDatatype = model.design.dataTypesDesign.distinctTypeSet.find{dt -> dt.name.toString() == attr.type.toString()}
     	use = 2
     }
     // Als we geen distinct type kunnen vinden, kijken we of we een structured type kunnen vinden op basis van de classifier id dat wijst naar het object id van het structered type.
     if (!logicalDatatype) {
     	logicalDatatype = model.design.dataTypesDesign.structuredTypeSet.find{struct -> struct.getProperty('Id') == attr.classifier.toString()}
     	use = 3
     }
     // Als we dan nog niets vinden dan zettn we het use type weer op logical data type en wordt het 'String'
     if (!logicalDatatype) {
     	use = 1
     	logicalDatatype = design.getLogicalDatatypeSet().getLogTypeByName('String')
     }
     log "logisch data type: ${logicalDatatype}"
     // Pas de lengte of precision/scale toe voor de betreffende logische data types
     switch (logicalDatatype.toString().toUpperCase()) {
     	// types met size/lengte 
    	 	case 'BIT':
    	 	case 'BOOLEAN':
    	 		dataTypeSize = 1
    	 	break	
    	 	case 'CHAR':
    	 	case 'NCHAR':
    	 	case 'NVARCHAR':
    	 	case 'RAW':
    	 	case 'STRING':
    	 	case 'SYSNAME':
    	 	case 'UNIQUEIDENTIFIER':
    	 	case 'UROWID':
    	 	case 'VARCHAR':
    	 	     // We kijken eerst of de lengte ingevuld is, daarna kijken we of de formeleLengte (uit het formele patroon) iets oplevert, daarna of de regex een lengte oplevert anders vullen standaard 255 als lengte in.
    	 	     if ((attr.lengte ?: 0 ) > 0) {
    	 	     	dataTypeSize = attr.lengte
    	 	     } else if ((formeleLengte ?: 0) > 0) {
    	 	     	dataTypeSize = formeleLengte
    	 	     } else if (dataType[0][2]) {
    	 	     	dataTypeSize = dataType[0][2]
    	 	     } else {
    	 	     	dataTypeSize = '255'
    	 	     }
       	break	
       	// types met precision en scale
          case 'DATETIME2':
          case 'DECIMAL':
          case 'DECFLOAT':
          case 'DOUBLE':
          case 'FLOAT':
          case 'INTERVAL DAY TO SECOND':
          case 'INTERVAL YEAR TO MONTH':
          case 'MONEY':
          case 'NUMERIC':
          case 'SMALLMONEY':
          case 'TIMESTAMP WITH LOCAL TIME ZONE':
          case 'TIMESTAMP WITH TIME ZONE':
          case 'TIMESTAMP':
       		dataTypePrecision = attr.precision
       		dataTypeScale = attr.scale
       	break
     	//default: //alle andere typen hebben geen lengte/size of precision en scale 
   	}
   	def dataTypeFound = [ type: logicalDatatype, precision: dataTypePrecision, scale: dataTypeScale, size: dataTypeSize, use: use]
   	log "dataType: $dataTypeFound"
	return dataTypeFound
}
log "Start met domeinen"
// voeg de EA enums toe als domains
// de EA enumeraties zijn opgeslagen in de t_object tabel
def domain_query = """
select o.* 
     , p.name as package_name
  from t_object as o
  left
  join t_package as p
    on o.Package_ID = p.Package_ID 
    /* MIM, Fietsenwinkel MIM 1.2 , GGM-MIM, IMBAG, IMX-GEO, IMBRT */
 where (o.object_type = 'Enumeration') /* Enumeratie in MIM */
    /* GGM, IMX-GEO  */
    or (o.object_type = 'DataType' and o.stereotype = 'Enumeratie') 
"""
// we vullen de domain objecten in SDDM met de enumeratie data
sql.eachRow(domain_query) { e ->
     // check via het UUID of het domein al bestaat anders maak een nieuw object
	def domain = model.design.defaultDomainsList.find{ dom -> dom.objectID == e.ea_guid.toString()} ?: model.design.createDomain()
	domain.objectID = e.ea_guid
	// we gebruiken hier de EA notatie met :: als scheidings teken om de domein namen uniek te maken
	domain.name = "${e.package_name}::${e.name}"
     domain.comment = e.note
     domain.logicalDatatype = design.getLogicalDatatypeSet().getLogTypeByName('VARCHAR')
     // zet de default lengte op 255 (TODO: uitzoeken of we nog een lengte kunnen bepalen)
     domain.dataTypeSize = 255
     // de verschillende enumeratie waarden zijn opgevoerd als attributen in EA
     // in SDDM maken we een domain met een waarde - omschrijving lijst aan voor de enumeratie
     domain.valueList = new oracle.dbtools.crest.model.design.constraint.ConstraintEnumeration()
     sql.eachRow(t_attribute_query(e.object_id)){ a ->
        // de name gebruiken we als waarde en in de descripten zetten we de eventuele omschrijving
        domain.valueList.add(value=a.name.toString(), description=(a.notes ?: a.name).toString())
     }
     // voeg de tags toe als dynamische properties in SDDM
	def entity_tag_query = """
	select * 
	  from t_objectproperties 
	 where object_id = $e.object_id 
	 order 
	    by propertyid
	"""
     sql.eachRow(entity_tag_query){ p ->
          def tag_waarde = p.value ?: ''
          // TODO: hoe om te gaan met memo waarden.
     	domain.setProperty("$p.property","$tag_waarde")
     	// we zetten de omscrijving in een eigen dynamic property die als naam <property>-note heeft     	
     	if (p.notes) {
     		domain.setProperty("${p.property.toString()}-note",p.notes.toString())
     	}
     }
     // we voegen het object_id toe als extra property
     domain.setProperty('Id',e.object_id.toString())
     log "Domein $domain.name aangemaakt"
     domain.dirty = true
}
log "Klaar met domeinen"

log "Start met simple types"
// voeg de EA primitieve data typen toe als distinct types
// de EA primitieve data typen zijn opgeslagen in de t_object tabel
def simple_type_query = """
select o.* 
     , p.name as package_name
  from t_object as o
  left
  join t_package as p
    on o.Package_ID = p.Package_ID 
    /* MIM, Fietsenwinkel MIM 1.2 , GGM, IMBAG, IMX-GEO, IMBRT */
 where (o.object_type = 'PrimitiveType') 
    or (o.object_type = 'DataType' and o.stereotype in ('Primitief datatype','Simpel datatype','interface'))     
    or (o.object_type = 'Class' and o.stereotype in ('interface')) 
"""
sql.eachRow(simple_type_query) { e ->
	def distinctType = model.design.dataTypesDesign.distinctTypeSet.find{dt -> dt.name == e.name.toString()} ?: model.design.dataTypesDesign.createDistinctType()
	//log structuredType.toString()
	distinctType.objectID = e.ea_guid
	distinctType.name = "${e.name}"
	distinctType.comment = e.note
	/* We zoeken het equivalente data type op in de logische data typen van SDDM, indien niet gevonden zetten we string met lengte 255 als logisch data type */
	distinctType.logicalDataType	= design.getLogicalDatatypeSet().getLogTypeByName(e.name) ?: design.getLogicalDatatypeSet().getLogTypeByName('String')
     //precision	
     //scale
     if (distinctType.logicalDataType.name == 'String') {
     	distinctType.size = '255'
     }
     // voeg de tags toe als dynamische properties in SDDM
	def entity_tag_query = """
	select * 
	  from t_objectproperties 
	 where object_id = $e.object_id 
	 order 
	    by propertyid
	"""
     sql.eachRow(entity_tag_query){ p ->
          def tag_waarde = p.value ?: ''
     	distinctType.setProperty("$p.property","$tag_waarde")
     	// we zetten de omscrijving in een eigen dynamic property die als naam <property>-note heeft
     	if (p.notes) {
     		distinctType.setProperty("${p.property.toString()}-note",p.notes.toString())
     	}
     }
     distinctType.dirty = true
}
log "Klaar met simple types"


// voeg de EA complexe typen to als struct, ze zijn opgeslagen in de t_object tabel
def struct_query = """
select o.* 
     , p.name as package_name
     , top.value AS formeel_patroon
     , twi.value AS waarde_item
  from t_object as o
  left
  join t_package as p
    on o.Package_ID = p.Package_ID 
  /* haal het formele patroon op indien aanwezig */ 
  left
  join t_objectproperties AS top 
    on o.object_id = top.object_id
   and top.property IN ('Formeel patroon')    
  /* haal het formele patroon op indien aanwezig */ 
  left
  join t_objectproperties AS twi 
    on o.object_id = twi.object_id
   and twi.property IN ('Waarde-item')     
    /* MIM, Fietsenwinkel MIM 1.2, GGM, IMX-GEO, IMKAD */
 where (o.object_type = 'DataType' and o.stereotype = 'Gestructureerd datatype') 
    /* GGM, IMBAG */ 
    or (o.object_type = 'DataType' and o.stereotype = 'Complex datatype')
    /* Referentielijst en Codelijst zijn externe lijsten die niet als entiteit terug komen, maar als structs */
    or (o.object_type = 'DataType' and o.stereotype in ('Referentielijst','Codelijst'))  
    /* Gegevensgroeptype komt terug als een struct */
    or (o.object_type = 'Class' and o.stereotype in ('Gegevensgroeptype')) 
    /* Keuze objecten die attributen hebben worden als struct opgevoerd */
    or (o.object_type = 'DataType' and o.stereotype in ('Keuze','Union') and exists (select 1 from t_attribute a where a.object_id = o.object_id) ) 
"""
// we vullen de complexe typen in SDDM 
// Een funtie gemaakt, om het herhaalbaar uit tevoeren voor geneste stucture types.
def structuredTypes = {
	log "Start met complexe typen"
	sql.eachRow(struct_query) { e ->
		def structuredType = model.design.dataTypesDesign.structuredTypeSet.find{struct -> struct.objectID == e.ea_guid.toString()} ?: model.design.dataTypesDesign.createStructuredType()
		//log structuredType.toString()
		structuredType.objectID = e.ea_guid
		structuredType.name = "${e.name}"
		structuredType.comment = e.note
		def attributeStereotype = ''
		log "structured type: ${structuredType.name}"
	     // per object halen we de waarden op
	     sql.eachRow(t_attribute_query(e.object_id)){ de ->
	     	def dataElement = structuredType.attributesList.find{ elem -> elem.objectID == de.ea_guid.toString()} ?: structuredType.createTypeElement()
	     	dataElement.objectID = de.ea_guid
	     	dataElement.name = de.name
	     	dataElement.comment = de.notes
	          // map het data type
	          def dataType = attributeToLogicalType(de)
			dataElement.type = dataType.type
			dataElement.precision = dataType.precision
			dataElement.scale = dataType.scale
			dataElement.size = dataType.size
			attributeStereotype = de.stereotype
	     } 
	     //log "attribuut stereotype: ${attributeStereotype}"
	     // Voor structs met het stereotype Keuze en de attribuut stereotype 'Datatype' zetten we het hive data type op UNIONTYPE.
	     if (e.stereotype in ['Keuze','Union'] && attributeStereotype in ['Datatype','Union element'] ) {
	     	structuredType.hiveType = 'UNIONTYPE'
	     }     
	     // een codelijst heeft geen attributen. We gaan kijken of bij de tag 'Waarde-item' iets ingevuld is, anders geven we attribuut de naam waarde. 
	     if (e.stereotype == 'Codelijst') {
			def dataElement = structuredType.attributesList.find{ elem -> elem.objectID == "${e.ea_guid}-waarde-item"} ?: structuredType.createTypeElement()
			dataElement.objectID = "${e.ea_guid}-waarde-item"
			dataElement.name = (e.waarde_item ?: 'waarde')
			dataElement.comment = e.note
			dataElement.type = design.getLogicalDatatypeSet().getLogTypeByName('String')
			dataElement.size = '255'
	     }
	     
		// we voegen het object_id toe als extra property
	     structuredType.setProperty('Id', e.object_id.toString())
	     // voeg de tags toe als dynamische properties in SDDM
		def entity_tag_query = """
		select * 
		  from t_objectproperties 
		 where object_id = $e.object_id 
		 order 
		    by propertyid
		"""
	     sql.eachRow(entity_tag_query){ p ->
	          def tag_waarde = p.value ?: ''
	     	structuredType.setProperty("$p.property","$tag_waarde")
	     	// we zetten de omscrijving in een eigen dynamic property die als naam <property>-note heeft
	     	if (p.notes) {
	     		structuredType.setProperty("${p.property.toString()}-note",p.notes.toString())
	     	}
	     }
		     log "StructuredType $structuredType.name aangemaakt"
	     structuredType.dirty = true
	}	
	log "Klaar met complexe typen"
}
// 2x om embedded structure types mee te nemen. Voor nu ok.
// Een keuze kan een structure type zijn met data types van het type structure!
structuredTypes()
structuredTypes()

log "Start met entiteiten"
// voeg de klassen toe als entiteiten in SDDM
// de klassen zijn uniek binnen een package, dus halen we de package naam ook erbij op 
// de data types Keuze en Referentielijst worden ook opgevoert als entiteit!
// Relatie klasse attributen op relatie, dus geen Entity hiervoor!
def entity_query = """
select o.* 
     , p.name as package_name
  from t_object as o
  left
  join t_package as p
    on o.Package_ID = p.Package_ID
 where (o.object_type = 'Class' and o.stereotype in ('ObjectType','Koppelklasse','featureType','type'))
    /* Keuze objecten die een class keuze zijn hebben geen attributen! */
    or (o.object_type = 'DataType' and o.stereotype in ('Keuze') and not exists (select 1 from t_attribute a where a.object_id = o.object_id) ) 
"""
sql.eachRow(entity_query) { e ->
     //log "entity: $e"
     // check via het UUID of de entiteit al bestaat anders maak een nieuw object
	def entity = model.entitySet.find{ent -> ent.objectID == e.ea_guid.toString()} ?: model.createEntity()
	entity.objectID = e.ea_guid
	// we gebruiken hier de EA notatie met :: als scheidings teken
	entity.name = "${e.package_name}::${e.name}"
	entity.shortName = e.name
	entity.comment = e.note
	entity.setProperty('Id',e.object_id.toString())
	entity.setProperty('Stereotype',e.stereotype.toString())
	//entity.fwdEngineeringStrategyName = 'Table per child'
	log "classificationType ${entity.classificationType}"
	//Zet het ClassificationType op basis van het stereotype
     switch (e.stereotype.toString()) {
    	 	case 'Objecttype':
    	 	     if (e.Abstract ==  '1') {
    	 	     	entity.typeID = abstractObjectTypeCT.typeID
    	 	     } else {	
				entity.typeID = objectTypeCT.typeID
    	 	     }
    	 	break
    	 	/* omgezet naar structured type
    	 	case 'Gegevensgroeptype':
			entity.typeID = gegevensgroepTypeCT.typeID     	 	
       	break */
       	/* omgezet naar structured type
    	 	case 'Referentielijst':
			entity.typeID = referentieLijstCT.typeID    	 		
    	 	break */
    	 	case 'Keuze':
			entity.typeID = keuzeCT.typeID     	 	
       	break
       	/* omgezet naar structured type
    	 	case 'Codelijst':
			entity.typeID = codeLijstCT.typeID     	 	
       	break */	
     	//default: geen classificartion type
   	}

	log "stereotype: ${e.stereotype}"
	log entity.typeID
	
	//entity.classification = e.stereotype.toString()
	// voeg de tags toe als dynamische properties in SDDM
	def entity_tag_query = """
	select * 
	  from t_objectproperties 
	 where object_id = $e.object_id 
	 order 
	    by propertyid
	"""
     sql.eachRow(entity_tag_query){ p ->
          def tag_waarde = p.value ?: ''
     	entity.setProperty("$p.property","$tag_waarde")
     	// we zetten de omscrijving in een eigen dynamic property die als naam <property>-note heeft
     	if (p.notes) {
     		entity.setProperty("${p.property.toString()}-note",p.notes.toString())
     	}
     }
	log "Entiteit $entity.name aangemaakt"
     // voeg de attributen toe
     def keyName = "PK_${e.name}"
     sql.eachRow(t_attribute_query(e.object_id)){ a ->
          //log a.toString()
          //if (a.UpperBound == '1') {
     	def attribute = entity.attributes.find{att -> att.objectID == a.ea_guid.toString()} ?: entity.createAttribute()
	     attribute.objectID = a.ea_guid
	     attribute.name = a.name
	     attribute.comment = a.notes
	     attribute.nullsAllowed = a.lowerbound == '1' ? false : true
		// map het data type
          def dataType = attributeToLogicalType(a)
          // vanaf hier collection type meenemen
          def collectionType
          if (a.UpperBound == '1') {
          	attribute.use = dataType.use
          } else {
          	// collection type nodig
			collectionType = model.design.dataTypesDesign.collectionTypeSet.find{coll -> coll.objectID == "${a.ea_guid}_collection"} ?: model.design.dataTypesDesign.createCollectionType() 
			collectionType.objectID = "${a.ea_guid}_collection"
          	collectionType.name = "${a.name}_collection"
          	collectionType.type = "COLLECTION"
          	collectionType.maxLengthAsString = 255
	          collectionType.dataTypeWrapper.precision = dataType.precision
			collectionType.dataTypeWrapper.scale = dataType.scale
			collectionType.dataTypeWrapper.size = dataType.size
			collectionType.dataTypeWrapper.typeID = dataType.type.id
          	attribute.use = 4
     	}
          switch (attribute.use) {
          	case 0 :
          		attribute.domain = dataType.type
          	break
          	case 1 :
          	 	attribute.logicalDatatype = dataType.type
          	break
          	case 2 :
          	 	attribute.distinctType = dataType.type
          	break
          	case 3 :
          	 	attribute.structuredType = dataType.type
          	break      
          	case 4 :
          	 	attribute.collectionType = collectionType
          	break 
          }
		attribute.dataTypePrecision = dataType.precision
		attribute.dataTypeScale = dataType.scale
		attribute.dataTypeSize = dataType.size
		// We voegen een kandidaat sleutel toe met de attributen waarvan de isID property is gezet. 
		if (a.is_id == 1) {
			//def candidateKey = entity.uniqueIdentifiers.find{key -> key.name == keyName} ?: entity.createCandidateKey()
			def candidateKey = entity.uniqueIdentifiers.find{key -> key.name == keyName} ?: entity.createKeyObject()
			log a.id_ea_guid.toString()
			candidateKey.objectID = candidateKey.objectID ?: a.id_ea_guid
			candidateKey.name = keyName
			candidateKey.PK = true
			candidateKey.add(attribute)
			candidateKey.dirty = true
			//log "key attributes ${candidateKey.newElementsCollection}"
		} 
		attribute.setProperty("Id",a.id.toString())
		attribute.dirty = true
     }
     // een code lijst bevat in ieder geval een code veld
     if (e.object_type == 'DataType' && e.stereotype == 'Codelijst'){
     	def attribute_waarde = entity.attributes.find{att -> att.objectID == "${e.ea_guid}-code"} ?: entity.createAttribute()
     	attribute_waarde.objectID = "${e.ea_guid}-code"
     	attribute_waarde.name = 'code'
     	attribute_waarde.comment = 'het code attribuut van de codelijst met de waarde'
     	attribute_waarde.dirty = true
     }
     entity.dirty = true
}
log "Klaar met entiteiten"
log "Start met relaties"
// de EA Generalization connector gaan we als SDDM InheritanceRelation opvoeren

def association_query = """
select tc.*
     , so.ea_guid as start_ea_guid
     , so.name as start_object_name
     , sp.name as start_package_name
     , eo.ea_guid as end_ea_guid
     , eo.name as end_object_name
     , ep.name as end_package_name
  from t_connector as tc 
  join t_object as so
    on tc.start_object_id = so.object_id
  join t_package as sp
    on so.package_id = sp.package_id
  join t_object as eo
    on tc.end_object_id = eo.object_id 
  join t_package as ep  
    on eo.package_id = ep.package_id  
 where tc.connector_type in ('Association','Abstraction','Aggregation','Composition')
    -- in GGM kan het stereotype leeg zijn
   and (tc.stereotype <> 'Keuze' or tc.Stereotype is null)
   and 'proxyconnector' not in (lower(so.name),lower(eo.name)) 
"""
sql.eachRow(association_query) { r ->
	def relationship = model.relationSet.find{rel -> rel.objectID == r.ea_guid.toString()} ?: model.createRelation()
	log "naam: $r.name sourceCard: $r.sourcecard destCard: $r.destcard "
	relationship.objectID = r.ea_guid
	//relationship.name =  "${r.start_package_name}::${r.start_object_name} ${r.name ?: '->'} ${r.end_package_name}::${r.end_object_name}"
	relationship.comment = r.notes
	relationship.sourceEntity = (model.entitySet.find{ent -> ent.objectID == r.start_ea_guid.toString()})
	if (r.connector_type != 'Abstraction') {
		relationship.name =  "${r.start_package_name}::${r.start_object_name} ${r.name ?: '->'} ${r.end_package_name}::${r.end_object_name}" 
		def sourceCard = r.sourcecard ?: '1'	
		//log "sourceCard: ${sourceCard}"
		relationship.optionalSource = sourceCard.toString()[0] == '0' ? true : false 
		relationship.sourceCardinalityString = r.sourcecard.toString().takeRight(1) == '*' ? '*' : '1'
		def targetCard = r.destcard ?: '1'
		//log "targetCard: ${targetCard}"
		relationship.targetEntity = (model.entitySet.find{ent -> ent.objectID == r.end_ea_guid.toString()})
		relationship.optionalTarget = targetCard.toString()[0] == '0' ? true : false 
		relationship.targetCardinalityString = r.destcard.toString().takeRight(1) == '*' ? '*' : '1'
		// We hebben hier een Relatieklasse dependency die is vastgelegd in PDATA1
		if (r.subtype == 'Class') {
		     // voeg de attributen toe
		     def relation_attribute_query = """
		     select a.*
		       from t_attribute AS a
		      where a.object_id = $r.pdata1   
		      order 
		         by pos
		     """
		     sql.eachRow(relation_attribute_query){ ra ->
		          //log a.toString()
		     	def relationship_attribute = relationship.attributesCollection.find{r_att -> r_att.objectID == ra.ea_guid.toString()} ?: relationship.createAttribute()
			     relationship_attribute.objectID = ra.ea_guid
			     relationship_attribute.name = ra.name
			     relationship_attribute.comment = ra.notes
			     relationship_attribute.nullsAllowed = ra.lowerbound == '1' ? false : true
			}
		}
	} else {	// Abstraction
		relationship.name =  "${r.start_package_name}::${r.start_object_name} ${r.name ?: 'trace'} ${r.end_package_name}::${r.end_object_name}"
		relationship.optionalSource = true 
		relationship.sourceCardinalityString = '1'
		relationship.targetEntity = (model.entitySet.find{ent -> ent.objectID == r.end_ea_guid.toString()})
		relationship.optionalTarget = true 
		relationship.targetCardinalityString = '1' 		
	}
	log relationship.toString()
	relationship.dirty = true
}
log "Klaar met relaties"
log "Start met inheritances"
def inheritance_query = """
select tc.*
     , so.ea_guid as start_ea_guid
     , so.name as start_object_name
     , sp.name as start_package_name
     , eo.ea_guid as end_ea_guid
     , eo.name as end_object_name
     , ep.name as end_package_name
  from t_connector as tc 
  join t_object as so
    on tc.start_object_id = so.object_id
  join t_package as sp
    on so.package_id = sp.package_id
  join t_object as eo
    on tc.end_object_id = eo.object_id 
  join t_package as ep  
    on eo.package_id = ep.package_id  
 where (tc.connector_type = 'Generalization' 
        or (tc.connector_type = 'Association' 
            and tc.stereotype = 'Keuze'
           )
        or (tc.connector_type = 'Dependency'
            and eo.stereotype = 'Gegevensgroeptype'
           )
       )
   and 'proxyconnector' not in (lower(so.name),lower(eo.name)) 
"""
sql.eachRow(inheritance_query) { r ->
     def sourceEntity = (r.connector_type == 'Generalization' || r.connector_type == 'Dependency') ? (model.entitySet.find{ent -> ent.objectID == r.start_ea_guid.toString()}) : (model.entitySet.find{ent -> ent.objectID == r.end_ea_guid.toString()})
     def targetEntity = (r.connector_type == 'Generalization' || r.connector_type == 'Dependency') ? (model.entitySet.find{ent -> ent.objectID == r.end_ea_guid.toString()}) : (model.entitySet.find{ent -> ent.objectID == r.start_ea_guid.toString()})
     if (sourceEntity && targetEntity) {
	     sourceEntity.hierarchicalParent = targetEntity
	     sourceEntity.dirty = true
	     def inheritance =  sourceEntity.inheritanceRelation
		inheritance.objectID = r.ea_guid
		inheritance.name =  "${r.start_package_name}::${r.start_object_name} ${r.name ?: '->'} ${r.end_package_name}::${r.end_object_name}"
		inheritance.comment = r.notes
		inheritance.dirty = true
     }
 }
log "Klaar met inheritances"

log "Start met de subviews"
// Subviews voor iedere package en diagram. We maken een soort van inhoudsopgave achtige indeling om de boomstructuur van EA te simuleren.
// Daardoor krijgen we veel lege subviews. We zeten tussen [] het aantal klassen in de subview, zodat we de lege subviews kunnen herkennen.
// We maken voor de packages boomstructuur met paden, zodat we die als subviews kunnen laten zien.
def tree_query = """
select parent_id
     , package_id
     , name
  from t_package p
 where parent_id <> 0   
 union all
select package_id as parent_id
     , diagram_id * 100000 as package_id 
     , name
  from t_diagram d  
 order 
    by parent_id
     , package_id    
"""
// Vullen van hulp sets
def prev_parent_id = -1
def rn = 1
def parents = [:]
def childern = [:]
def guids = [:]
def numbers = [:]
def names = [:]
sql.eachRow(tree_query) { s ->
     // nummer de kinderen binnen de ouders 
     if (prev_parent_id != s.parent_id) {
     	rn = 1
     } else {
     	rn = rn + 1
     }
     // maak een set van ouders (keys in de map zijn uniek!)
     parents["$s.parent_id"] = ''
     // een map met de kinderen en de ouder
     childern["$s.package_id"] = "$s.parent_id"
     // een map met de kinderen en het 'index' nr
     numbers["$s.package_id"] = "$rn".padLeft( 2, '0' )
     // een map met de namen
     names["$s.package_id"] = "$s.name"
     // zet de 'vorige' ouder, nodig voor het nummeren van de kinderen binnen de ouder
     prev_parent_id = s.parent_id
}
// Aanmaken van de paden
def leafs = childern.keySet() //- parents.keySet()
def paths = [:]
leafs.each{ k ->
	def path = [k]
	def parent = k
	while(parent = childern[parent]) {	
		path = [parent] + path  
	}
	paths["$k"] = path
	//log "$k $path"
}
// De package_id vervangen door het pad nr in de boom
def path_nums =[:]
def path_names = [:]
paths.each{ k, v ->
     def nums = []
     def namen = []
	v.each{ pack_id ->
		nums << (numbers[pack_id] ?: '00')
		namen << (names[pack_id] ?: '')
	}
	path_nums[k] = nums
	path_names[k] = namen
}
// we vullen de subviews met de package en diagram info
def diagram_query = """
select package_id as parent_id
     , diagram_id * 100000 as package_id
     , name
     , ea_guid
     , notes
     , stereotype
     , case when pdata like '%HideAtts=1%' then 1 else 0 end as hide_atts
     , (select count(1) 
          from t_diagramobjects 
          join t_object 
            ON t_diagramobjects.object_id = t_object.object_id
         where t_diagramobjects.diagram_id = d.diagram_id     
           and t_object.object_type = 'Class' 
           and t_object.stereotype in ('ObjectType'/*,'Koppelklasse')*/)
       ) as aantal
     , 'diagram' as subview_type         
  from t_diagram as d  
 order 
    by package_id 
"""
def subview_query = """
select p.parent_id
     , p.Package_ID 
     , p.Name 
     , p.ea_guid
     , coalesce(p.Notes, o.Note) as notes
     , o.stereotype
     , (select count(1) 
          from t_object 
         where package_id = p.package_id 
           and ((object_type = 'Class' and stereotype in ('ObjectType'/*,'Koppelklasse')*/) /*or (object_type = 'DataType' and stereotype = 'Referentielijst'))*/
       ) as aantal
     , 'package' as subview_type  
  from t_package p
  left
  join t_object o
    on p.ea_guid = o.ea_guid
 where p.parent_id <> 0     
 union all 
select package_id as parent_id
     , diagram_id * 100000 as package_id
     , name
     , ea_guid
     , notes
     , stereotype
     , (select count(1) 
          from t_diagramobjects 
          join t_object 
            ON t_diagramobjects.object_id = t_object.object_id
         where t_diagramobjects.diagram_id = d.diagram_id
           and ((object_type = 'Class' and stereotype in ('ObjectType'/*,'Koppelklasse')*/) /*or (object_type = 'DataType' and stereotype = 'Referentielijst'))*/
       ) as aantal
     , 'diagram' as subview_type         
  from t_diagram as d  
 order 
    by package_id  
"""
// We maken de subviews aan en prefixen ze met het gevonden pad
sql.eachRow(diagram_query) { s ->
     if (s.aantal > 0) {
		def subview = model.listOfSubviews.find{ sub -> sub.DPVId == s.ea_guid.toString()} ?: model.createDesignPartSubView()
		subview.boxInBox = false
		subview.DPVId = s.ea_guid
	     // Haal het juiste pad op en strip de 00. om het pad iets korter te maken
		def prefix_index = ((path_nums[s.package_id.toString()] ?: []).join('.')).replace('00.01.','')
		// verwijder de Root.name uit het pad
		def prefix = ((path_names[s.package_id.toString()] ?: []).join('/')).replace("/${Root.name}",'')
		// tbv sortering van de 'hoofd' packages in het GGM
		prefix = prefix.replace('/0 ','/00 ')
		prefix = prefix.replace('/1 ','/01 ')
		prefix = prefix.replace('/2 ','/02 ')
		prefix = prefix.replace('/3 ','/03 ')
		prefix = prefix.replace('/4 ','/04 ')
		prefix = prefix.replace('/5 ','/05 ')
		prefix = prefix.replace('/6 ','/06 ')
		prefix = prefix.replace('/7 ','/07 ')
		prefix = prefix.replace('/8 ','/08 ')
		prefix = prefix.replace('/9 ','/09 ')
		// verwijder Diagram uit het pad
		prefix = prefix.replace('/Diagram/','/')
		//def aantal = "[$s.aantal]"
		// Voeg 00 aan het eind toe voor correcte sortering van de paden. Zet tussen [] het aantal entities in deze subview.
		//def name = "${prefix_index}.00 - [$s.aantal] $s.name"
		def name = "${prefix} [$s.aantal]"
	     subview.name = name
	     subview.comment = s.notes
	     subview.showRelAttributes = true
	     if (s.hide_atts == 1) {
	     	subview.showNamesOnly = true
	     }
	     // we voegen de Class objecten toe aan de subview
	     if (s.subview_type == 'package' && s.aantal > 0) {
	     	subview.getPlaceHolder().visible = true
	     	def subview_object_query = """
	     	select *
			  from t_object
			 where package_id = $s.package_id
			   and object_type = 'Class'
			   and stereotype = 'ObjectType'
			   
	     	"""	
	     	sql.eachRow(subview_object_query) { o ->
	     		def subview_package_entity = (model.entitySet.find{ent -> ent.objectID == o.ea_guid.toString()})
	     		subview.addViewFor(subview_package_entity)
	     	}
	     } else if (s.subview_type == 'diagram' && s.aantal > 0) {
	     	subview.getPlaceHolder().visible = true
	     	def subview_diagram_query = """
	     	select o.*
			  from t_diagram as d
			  join t_diagramobjects as dobj
			    on d.diagram_id = dobj.diagram_id
			  join t_object as o
			    on o.object_id = dobj.object_id
			 where d.ea_guid = '$s.ea_guid'
			   and ((o.object_type = 'Class' and o.stereotype in ('ObjectType','Koppelklasse','featureType','type')) 
    				   or (o.object_type = 'DataType' and o.stereotype in ('Keuze','Union') and not exists (select 1 from t_attribute a where a.object_id = o.object_id) ) 
                      )
	     	"""	
	     	sql.eachRow(subview_diagram_query) { o ->
	     		def subview_diagram_entity = (model.entitySet.find{ent -> ent.objectID == o.ea_guid.toString()})
	     		subview.addViewFor(subview_diagram_entity)
	     		entityView = subview_diagram_entity.getFirstViewForDPV(subview)
	     		entityView.addTVRelations()
	     	}  
	     }
	     // zet het diagram weer op 'invisible'
	     subview.getPlaceHolder().visible = false
	     subview.setProperty('Id',s.package_id.toString())
	     log "Subview $name created"
	     subview.dirty = true
	     subview.rearrangeNewDiagram()
     }
}
log "Klaar met de subviews"
model.dirty = true