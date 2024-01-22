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

    ArrayList root = new JsonSlurper().parse(is).PivotOnConfigs[1..-1]



    // --- calculate numKeys --- //

    ArrayList numKeysArr = []
    root.each { item ->
      numKeysArr << item.Col16.split(DBIFS).size()
    }
    // println numKeysArr

    int numKeys
    if (numKeysArr.unique().size() == 1) {
      numKeys = numKeysArr.unique()[0]
    } else {
      println "UH OH"
    }



    // --- main logic --- //

    rowArr = []

    root.each { item ->
      rowArr << [
        PivotedDataConfigId: item.Col18,
        ColumnKey: item.Col16,
        ColumnLabels: item.Col17,
        Active: item."Col${numKeys+0}",
        SuppressIfNoDataForAllRows: item."Col${numKeys+3}",
        ColumnIndex: item."Col19",
        SubTableIndex: item."Col${numKeys+1}",
        ColumnWidth: item."Col${numKeys+2}",
      ]
    }
    println prettyJson(rowArr)



    // --- result --- //

    is = new ByteArrayInputStream(prettyJson(rowArr).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { 
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}
