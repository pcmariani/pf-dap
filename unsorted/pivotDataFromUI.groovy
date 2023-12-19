import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();


for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()
    def pivotedDataConfigsArr = new JsonSlurper().parse(is).Records

    def resultArr = []

    // current UI can't handle real booleans
    def convertBools = { bool ->
        if (bool) return "T"
        else return "F"
    }

    // headers row
    def headerRow = [:]
    def headerRowInData = [:]
    headerRow["Col0"] = "Active"
    headerRow["Col1"] = "SuppressIfNoDataForAllRows"
    if (!transpose) headerRow["Col2"] = "SubTableIndex"
    if (!transpose) headerRow["Col3"] = "ColumnWidth"
    pivotedDataConfigsArr[0].PivotedItemLabels.eachWithIndex { item, j ->
        int num = 4 + j * 2
        // headerRow["Col${num}"] = "PivotOn Column " + (j+1).toString()
        headerRow["Col${num+1}"] = "PivotOn Column " + (j+1).toString() + " Label"
    }
    // println prettyJson(headerRow)

    headerRowInData = headerRow.clone()
    headerRowInData["Col0"] = true
    headerRowInData["Col1"] = true
    // println prettyJson(headerRowInData)

    resultArr << headerRowInData

    // data rows
    pivotedDataConfigsArr.each { record ->
        def dataRow = [:]
        // dataRow["Col0"] = convertBools(record.Active)
        // dataRow["Col1"] = convertBools(record.SuppressIfNoDataForAllRows)
        dataRow["Col0"] = record.Active
        dataRow["Col1"] = record.SuppressIfNoDataForAllRows
        dataRow["Col2"] = record.SubTableIndex
        dataRow["Col3"] = record.ColumnWidth
        record.PivotedItemLabels.eachWithIndex { item, j ->
            int colIndex = 4 + j * 2
            dataRow["Col${colIndex}"] = item.Name
            dataRow["Col${colIndex+1}"] = item.Label
        }
        dataRow["Col18"] = record.PivotedDataConfigId
        dataRow["Col19"] = record.ColumnIndex
        resultArr << dataRow
    }

    is = new ByteArrayInputStream(prettyJson([
        HeaderRows: [headerRow],
        PivotOnConfigs: resultArr
    ]).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
