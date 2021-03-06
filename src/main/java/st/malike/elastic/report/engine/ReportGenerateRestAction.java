package st.malike.elastic.report.engine;

import static org.elasticsearch.rest.RestRequest.Method.POST;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.sort.SortOrder;
import st.malike.elastic.report.engine.generate.GenerateData;
import st.malike.elastic.report.engine.generate.GenerateResponseListener;
import st.malike.elastic.report.engine.service.Generator;
import st.malike.elastic.report.engine.service.Generator.JSONResponseMessage;
import st.malike.elastic.report.engine.util.JSONResponse;

/**
 * @author malike_st.
 */
public class ReportGenerateRestAction extends BaseRestHandler {

  @Inject
  public ReportGenerateRestAction(Settings settings, RestController controller) {
    super(settings);
    controller.registerHandler(POST, "/_generate", this);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client)
      throws IOException {
    JSONResponse message = new JSONResponse();
    GenerateData generateData = new GenerateData();
    Integer from = null;
    Integer size = null;
    Map<String, String> sort = new HashMap();
    String query = null;
    String format = null;
    Generator.ReturnAs returnAs = Generator.ReturnAs.BASE64;
    String templateLocation = null;
    String fileName = null;
    String index = null;
    Map mapData = new HashMap();

    if (restRequest.content().length() > 0) {
      // Let's try to find the name from the body
      Map<String, Object> map = XContentHelper.convertToMap(restRequest.content(), false, null)
          .v2();
      if (map.containsKey("query")) {
        query = (String) map.get("query");
      }
      if (map.containsKey("from")) {
        from = (Integer) map.get("from");
      }
      if (map.containsKey("size")) {
        size = (Integer) map.get("size");
      }
      if (map.containsKey("sort")) {
        sort = (Map) map.get("sort");
      }
      if (map.containsKey("query")) {
        query = (String) map.get("query");
      }
      if (map.containsKey("format")) {
        format = (String) map.get("format");
      }
      if (map.containsKey("returnAs")) {
        try {
          returnAs = Generator.ReturnAs.valueOf(((String) map.get("returnAs")).toUpperCase());
        } catch (Exception e) {
        }
      }
      if (map.containsKey("template")) {
        templateLocation = (String) map.get("template");
      }
      if (map.containsKey("fileName")) {
        fileName = (String) map.get("fileName");
      }
      if (map.containsKey("index")) {
        index = (String) map.get("index");
      }
      if (map.containsKey("template")) {
        templateLocation = (String) map.get("template");
      }
      if (map.containsKey("mapData")) {
        mapData = (Map) map.get("mapData");
      }
    }
    if ((format == null || index == null)
        || ((format.toLowerCase().equals("pdf") || (format.toLowerCase().equals("html")))
        && templateLocation == null)) {
      return channel -> {
        message.setMessage(JSONResponseMessage.MISSING_PARAM.toString());
        message.setStatus(false);
        message.setCount(0L);
        channel.sendResponse(new BytesRestResponse(RestStatus.OK,
            "application/json", message.toString()));
      };
    }
    Generator.ReportFormat reportFormat = null;
    try {
      reportFormat = Generator.ReportFormat.valueOf(format.toUpperCase());
    } catch (Exception e) {
    }
    if (reportFormat == null) {
      return channel -> {
        message.setMessage(JSONResponseMessage.REPORT_FORMAT_UNKNOWN.toString());
        message.setStatus(false);
        message.setCount(0L);
        channel.sendResponse(new BytesRestResponse(RestStatus.OK,
            "application/json", message.toString()));
      };
    }
    SearchRequestBuilder prepareSearch = client.prepareSearch(index);
    //limit and offset
    if (from != null && size != null) {
      prepareSearch.setFrom(from);
      prepareSearch.setSize(size);
    }

    //sort
    if (!sort.isEmpty()) {
      for (String key : sort.keySet()) {
        try {
          prepareSearch.addSort(key, SortOrder.fromString(sort.get(key).toUpperCase()));
        } catch (Exception e) {

        }
      }
    }

    prepareSearch.setSearchType(SearchType.QUERY_THEN_FETCH);
    if (query != null && !query.isEmpty()) {
      prepareSearch.setQuery(QueryBuilders.wrapperQuery(query));
    } else {
      prepareSearch.setQuery(QueryBuilders.matchAllQuery());
    }
    prepareSearch.setScroll("3m");
    prepareSearch.setTimeout(TimeValue.timeValueMinutes(3));
    generateData.setFileName(fileName);
    generateData.setFormat(reportFormat);
    generateData.setIndex(index);
    generateData.setMapData(mapData);
    generateData.setQuery(query);
    generateData.setTemplateLocation(templateLocation);
    generateData.setReturnAs(returnAs);
    return (channel) -> prepareSearch
        .execute(new GenerateResponseListener(generateData, channel, restRequest));
  }

}
