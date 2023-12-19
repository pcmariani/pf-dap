import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;
 
logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def root = new JsonSlurper().parse(is)
    def paramNamesFormatted = root.params.collect{"\"" + it.name + "\""}.join(", ")
    def filters = root.filters
    def filtersFormatted = filters.collect{k,v->"\"$k\" = '$v'"}.join(" AND ")

    def SqlQueryForParamValues =
        "SELECT DISTINCT " + paramNamesFormatted +
        " FROM " + root.table +
        ( filters ? " WHERE " + filtersFormatted : "" ) +
        " GROUP BY " + paramNamesFormatted +
        " ORDER BY " + paramNamesFormatted

    props.setProperty("document.dynamic.userdefined.ddp_sqlStatement", SqlQueryForParamValues)

    root << [ListOfValuesSqlQuery: SqlQueryForParamValues]

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(root)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
