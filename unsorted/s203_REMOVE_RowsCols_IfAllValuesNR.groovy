import java.util.Properties;
import java.io.InputStream;
import groovy.xml.XmlUtil
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;
 
def NEWLINE = System.lineSeparator()
def IFS = /\|\^\|/  // Input Field Separator
def OFS = "|^|"     // Output Field Separator

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigs")
    def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson).Records : []
    // println prettyJson(pivotedDataConfigsArr)
    // println pivotedDataConfigsArr.size()
    def newPivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_NewPivotedDataConfigs")
    def newPivotedDataConfigsArr = newPivotedDataConfigsJson ? new JsonSlurper().parseText(newPivotedDataConfigsJson).Records : []
    // println prettyJson(newPivotedDataConfigsArr)
    // println newPivotedDataConfigsArr.size()

    // Boolean isPivot = (props.getProperty("document.dynamic.userdefined.ddp_isPivot") ?: "true").toBoolean()
    // println isPivot
    Boolean transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()
    // println transpose
    def numHeaderRows = (props.getProperty("document.dynamic.userdefined.ddp_numHeaderRows") ?: "1") as int
    // println numHeaderRows
    def numGroupByCols = (props.getProperty("document.dynamic.userdefined.ddp_numGroupByCols") ?: "0") as int
    // println numGroupByCols

    def reader = new BufferedReader(new InputStreamReader(is))

    def dataArr = []
    while ((line = reader.readLine()) != null ) {
        dataArr << line.split(/\s*$IFS\s*/)
    }

    if (transpose) {
        def numHeaderRowsTmp = numHeaderRows
        numHeaderRows = numGroupByCols
        numGroupByCols = numHeaderRowsTmp
        dataArr = dataArr.transpose()
        // println dataArr[0].size()
    }
    // dataArr.each { println it }

    def pivotedDataConfigsActive = (pivotedDataConfigsArr + newPivotedDataConfigsArr).findAll { it.Active == true }
    // println pivotedDataConfigsActive.SuppressIfNoDataForAllRows
    // println pivotedDataConfigsActive.SuppressIfNoDataForAllRows.size()

    // loop through pivoted columns configs and correpsonding columns in the data
    // (transposing allows looping by column)
    def newDataTransposedArr = dataArr.transpose()[0..numGroupByCols-1]
    // println newDataTransposedArr
    pivotedDataConfigsActive.eachWithIndex { configItem, j ->
        // evaluate column data only ignoring header columns (numGroupByCols) and header rows (numHeaderRows)
        def allColumnValuesAreNR = dataArr.transpose()[j+numGroupByCols][numHeaderRows..-1].clone().unique() == ["NR"]
        // println j + "  |  " + configItem.SuppressIfNoDataForAllRows + "  |  " + allColumnValuesAreNR + "  |  " + dataArr.transpose()[j+numGroupByCols]
        if (configItem.SuppressIfNoDataForAllRows && allColumnValuesAreNR) true // do nothing
        else {
            newDataTransposedArr << dataArr.transpose()[j+numGroupByCols]
        }
    }

    // println newDataTransposedArr.size()
    if (!transpose) newDataTransposedArr = newDataTransposedArr.transpose()

    def outData = new StringBuffer()
    newDataTransposedArr.each { row ->
        outData.append(row.join(OFS) + NEWLINE)
    }


    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
