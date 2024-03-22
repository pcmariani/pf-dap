import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

String DBIFS = /\^\^\^/
logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {

    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = sourcesJson ? new JsonSlurper().parseText(sourcesJson).Records[0] : []
    // println prettyJson(sources)

    ArrayList root = new JsonSlurper().parse(is).Records

    LinkedHashMap outData

    // empty input
    if (!root) {
      outData = []
    }

    // UPDATE response from Db only contains Ids
    else if (root.RowKey.unique() == [null]) {
      outData = [
        HeaderColumns: [["Col18": "GroupByRowsConfigId"]],
        GroupByRowsConfigs: root.GroupByRowsConfigId.collect{ ["Col18": it]}
      ]
    }

    // READ response
    else {
        // --- calculate numKeysCols --- //

        ArrayList numKeysColsArr = []
        root.each { item ->
          numKeysColsArr << item.RowKey.split(DBIFS).size()
        }
        // println numKeysColsArr

        int numKeysCols
        if (numKeysColsArr.unique().size() == 1) {
          numKeysCols = numKeysColsArr.unique()[0]
        } else {
          println "UH OH"
        }


        // --- main logic --- //

        LinkedHashMap headerRow = [:]
        typesRowArr = []
        rowArr = []

        Boolean firstItem = true
        root.each { item ->

          LinkedHashMap row = [:]
          LinkedHashMap typesRow = [:]

          def rowKeyArr = item.RowKey.split(DBIFS)
          def rowLabelsArr = item.RowLabels.split(DBIFS)

          rowKeyArr.eachWithIndex { key, k ->
            if (key) {
              int rowIndex = sources.GroupByConfigRowLabelsEditable ? k : k + 5
              def config = sources.PivotGroupByColumns[k]
              def headerLabel = config.Column + (config.Label ? " ("+config.Label+")" : "")
              if (firstItem) {
                headerRow << ["Col$rowIndex": headerLabel]
                typesRow << ["Col$rowIndex": "String"]
              }
              row << ["Col$rowIndex": rowLabelsArr[k].replaceAll(/\\n/,"\n")]
            }
          }

          if (firstItem) {
            headerRow << [
              "Col10": "Active",
              "Col11": "Suppress Row If No Data For All Cols",
              "Col16": "RowKey",
              "Col17": "RowLabels",
              "Col18": "GroupByRowsConfigId",
              "Col19": "RowIndex"
            ]
            typesRow << [
              "Col10": true,
              "Col11": true,
              "Col16": "String",
              "Col17": "String",
              "Col18": "int",
              "Col19": "int"
            ]

            rowArr << typesRow
          }

          row << [
            "Col10": item.Active,
            "Col11": item.SuppressIfNoDataForAllCols,
            "Col16": item.RowKey,
            "Col17": item.RowLabels.replaceAll(/\\n/,"\n"),
            "Col18": item.GroupByRowsConfigId,
            "Col19": item.RowIndex
          ]

          rowArr << row

          firstItem = false
        }

        def headerRowArr = [headerRow]

        // println headerRowArr
        // println rowArr



        // --- result --- //

        outData = [ 
          HeaderColumns: headerRowArr,
          GroupByRowsConfigs: rowArr
        ]
        // println prettyJson(outData)
    }


    is = new ByteArrayInputStream(prettyJson(outData).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { 
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}
