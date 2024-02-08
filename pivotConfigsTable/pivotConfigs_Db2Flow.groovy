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

    ArrayList root = new JsonSlurper().parse(is).Records

    def outData

    // empty input
    if (!root) {
      outData = []
    }

    // UPDATE response from Db only contains Ids
    else if (root.ColumnKey.unique() == [null]) {
      outData = [
        HeaderRows: [["Col18": "PivotedDataConfigId"]],
        PivotOnConfigs: root.PivotedDataConfigId.collect{ ["Col18": it]}
      ]
    }

    // READ response
    else {
        // --- calculate numKeys --- //

        ArrayList numKeysArr = []
        root.each { item ->
          numKeysArr << item.ColumnKey.split(DBIFS).size()
        }
        // println numKeysArr

        int numKeys
        if (numKeysArr.unique().size() == 1) {
          numKeys = numKeysArr.unique()[0]
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

          def columnKeyArr = item.ColumnKey.split(DBIFS)
          def columnLabelsArr = item.ColumnLabels.split(DBIFS)

          columnKeyArr.eachWithIndex { key, k ->
            if (key) {
              if (firstItem) {
                headerRow << ["Col$k": "Column Label ${k+1}"]
                typesRow << ["Col$k": "String"]
              }
              row << ["Col$k": columnLabelsArr[k]]
            }
          }

          if (firstItem) {
            headerRow << [
              "Col9": "Active",
              "Col10": "Suppress Column If No Data For All Rows",
              "Col11": "Sub-Table Index",
              "Col12": "Column Width",
              "Col16": "ColumnKey",
              "Col17": "ColumnLabels",
              "Col18": "PivotedDataConfigId",
              "Col19": "ColumnIndex"
            ]
            typesRow << [
              "Col9": true,
              "Col10": true,
              "Col11": "int",
              "Col12": "int",
              "Col16": "String",
              "Col17": "String",
              "Col18": "int",
              "Col19": "int"
            ]

            rowArr << typesRow
          }

          row << [
            "Col9": item.Active,
            "Col10": item.SuppressIfNoDataForAllRows,
            "Col11": item.SubTableIndex,
            "Col12": item.ColumnWidth,
            "Col16": item.ColumnKey,
            "Col17": item.ColumnLabels,
            "Col18": item.PivotedDataConfigId,
            "Col19": item.ColumnIndex
          ]

          rowArr << row

          firstItem = false
        }

        def headerRowArr = [headerRow]

        // println headerRowArr
        // println rowArr



        // --- result --- //

        outData = [ 
          HeaderRows: headerRowArr,
          PivotOnConfigs: rowArr
        ]
        // println prettyJson(outData)
    }   


    is = new ByteArrayInputStream(prettyJson(outData).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { 
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}
