package com.sap.gtt.v2.sample.pof.service.client;

import com.sap.gtt.v2.sample.pof.configuration.Destination;
import com.sap.gtt.v2.sample.pof.configuration.VcapParser;
import com.sap.gtt.v2.sample.pof.constant.Constants;
import com.sap.gtt.v2.sample.pof.domain.Location;
import com.sap.gtt.v2.sample.pof.exception.LocationServiceException;
import com.sap.gtt.v2.sample.pof.exception.MetadataServiceException;
import com.sap.gtt.v2.sample.pof.exception.ReadServiceException;
import com.sap.gtt.v2.sample.pof.exception.WriteServiceException;
import com.sap.gtt.v2.sample.pof.odata.filter.FilterCondition;
import com.sap.gtt.v2.sample.pof.odata.filter.FilterExpressionBuilder;
import com.sap.gtt.v2.sample.pof.odata.helper.ODataResultList;
import com.sap.gtt.v2.sample.pof.utils.ODataUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.apache.olingo.odata2.api.uri.expression.BinaryOperator;
import org.apache.olingo.odata2.api.uri.expression.FilterExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GTTCoreServiceClient {
    public static final String OUTBOUND_ODATA_V_1 = "/outbound/odata/v1/";
    public static final String INBOUND_REST_V_1 = "/inbound/rest/v1/";
    public static final String METADATA_V_1_MODEL = "/metadata/v1/model/";
    public static final String METADATA_V_1_MODEL1 = "/metadata/v1/model/";

    private static final Logger logger = LoggerFactory.getLogger(GTTCoreServiceClient.class);
    public static final String LOCATION_V_1_LOCATION = "/location/v1/Location";
    public static final String FORMAT = "$format";
    public static final String JSON = "json";
    public static final String FILTER = "$filter";
    public static final String ORDERBY = "$orderby";
    public static final String INLINECOUNT = "$inlinecount";
    public static final String ALLPAGES = "allpages";
    public static final String INLINECOUNT_ALLPAGES = INLINECOUNT + "=" + ALLPAGES;
    public static final String INLINECOUNT_NONE = "$inlinecount=none";
    public static final String SKIP = "&$skip=";

    @Value("${DESTINATION_GTT:#{null}}")
    private String destinationGTT;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private VcapParser vcapParser;

    @PostConstruct
    public void init() {
        Destination destination = vcapParser.getDestination(destinationGTT);
        gttBaseUrl = destination.getUrl();
        techUser = destination.getUser();
        criticalInfo = destination.getPassword();
    }

    //@Value("${GTT_CORE_ENGINE_API_URL_FLP_BASED}")
    private String gttBaseUrl;

    //@Value("${GTT_CORE_ENGINE_TECHNICAL_USER}")
    private String techUser;

    //@Value("${GTT_CORE_ENGINE_TECHNICAL_PWD}")
    private String criticalInfo;

    public <T> ODataResultList<T> readEntitySetAll(String uri, Class<T> classOfT, HttpHeaders headers) {
        uri = StringUtils.replaceIgnoreCase(uri, INLINECOUNT_NONE, INLINECOUNT_ALLPAGES);
        if (!StringUtils.containsIgnoreCase(uri, INLINECOUNT_ALLPAGES)) {
            if (!uri.contains("?")) {
                uri += "?" + INLINECOUNT_ALLPAGES;
            } else {
                uri += "&" + INLINECOUNT_ALLPAGES;
            }
        }

        ODataResultList<T> res = readEntitySet(uri, classOfT, headers);
        if (res.getCount() != null && res.getCount() > 2000) {
            logger.warn("total count is: {}, url: {}", res.getCount(), uri);
        }

        while (res.getCount() != null && res.getCount() > res.getResults().size()) {
            String skip = SKIP + res.getResults().size();
            uri += skip;
            ODataResultList<T> tmp = readEntitySet(uri, classOfT, headers);
            res.setCount(tmp.getCount());
            res.getResults().addAll(tmp.getResults());
            uri = uri.replace(skip, "");
        }
        return res;
    }

    public <T> ODataResultList<T> readEntitySetAll(String uri, Class<T> classOfT) {
        return readEntitySetAll(uri, classOfT, null);
    }

    public <T> ODataResultList<T> readEntitySet(String uri, Class<T> classOfT) {
        String json = query(uri);
        return ODataUtils.readEntitySet(json, classOfT);
    }

    public <T> T readEntity(String uri, Class<T> classOfT) {
        String json = query(uri);
        return ODataUtils.readEntity(json, classOfT);
    }

    public <T> ODataResultList<T> readEntitySet(String uri, Class<T> classOfT, HttpHeaders headers) {
        String json = query(uri, headers);
        return ODataUtils.readEntitySet(json, classOfT);
    }

    public <T> T readEntity(String uri, Class<T> classOfT, HttpHeaders headers) {
        String json = query(uri, headers);
        return ODataUtils.readEntity(json, classOfT);
    }

    public Integer countEntitySet(String uri) {
        String json = query(uri);
        return Integer.parseInt(json);
    }

    public String query(String uri) {
        return query(uri, null);
    }

    public String query(String uri, HttpHeaders headers) {
        HttpHeaders finalHeaders = new HttpHeaders();
        if (headers != null) {
            finalHeaders.addAll(headers);
        }
        setHeaders(finalHeaders);
        if (CollectionUtils.isEmpty(finalHeaders.getAcceptLanguage())) {
            finalHeaders.setAcceptLanguageAsLocales(Collections.singletonList(LocaleContextHolder.getLocale()));
        }

        String readServiceUrl = UriComponentsBuilder.fromHttpUrl(gttBaseUrl + OUTBOUND_ODATA_V_1 +
                Constants.MODEL_NAMESPACE + uri).build().toUriString();
        logger.info("ready to call read service: {}", readServiceUrl);

        ResponseEntity<String> responseEntity;
        try {
            responseEntity = restTemplate.exchange(readServiceUrl, HttpMethod.GET,
                    new HttpEntity<>(null, finalHeaders), String.class);
        } catch (HttpStatusCodeException e) {
            logger.error("Call read service failed:", e);
            throw new ReadServiceException(e, e.getStatusCode().value());
        }
        return responseEntity.getBody();

    }

    public void write(String body, String uri) {
        HttpHeaders headers = new HttpHeaders();
        setHeaders(headers);

        String writeServiceUrl = gttBaseUrl + INBOUND_REST_V_1 + Constants.GTT_MODEL_NAMESPACE_WRITE_SERVICE + uri;
        logger.debug("ready to call write service: {}", writeServiceUrl);
        try {
            restTemplate.exchange(writeServiceUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        } catch (HttpStatusCodeException e) {
            logger.error("Call write service failed:", e);
            throw new WriteServiceException(e, e.getStatusCode().value());
        }
    }

    private void setHeaders(HttpHeaders headers) {
        headers.setBasicAuth(techUser, criticalInfo);
    }

    public String getUiAnnotation() {
        HttpHeaders headers = new HttpHeaders();
        setHeaders(headers);

        String metadataServiceUrl = gttBaseUrl + METADATA_V_1_MODEL + Constants.GTT_MODEL_NAMESPACE + "/annotation";
        logger.debug("ready to call metadata service: {}", metadataServiceUrl);
        ResponseEntity<String> responseEntity;
        try {
            responseEntity = restTemplate.exchange(metadataServiceUrl, HttpMethod.GET,
                    new HttpEntity<>(null, headers), String.class);
        } catch (HttpStatusCodeException e) {
            logger.error("Call metadata service failed:", e);
            throw new MetadataServiceException(e, e.getStatusCode().value());
        }
        return responseEntity.getBody();
    }

    public String getI18n(String properties) {
        HttpHeaders headers = new HttpHeaders();
        setHeaders(headers);

        String metadataServiceUrl = gttBaseUrl + METADATA_V_1_MODEL1 + Constants.GTT_MODEL_NAMESPACE + "/i18n/" + properties;
        logger.debug("ready to call metadata service: {}", metadataServiceUrl);
        ResponseEntity<String> responseEntity;
        try {
            responseEntity = restTemplate.exchange(metadataServiceUrl, HttpMethod.GET,
                    new HttpEntity<>(null, headers), String.class);
        } catch (HttpStatusCodeException e) {
            logger.error("Call metadata service failed:", e);
            throw new MetadataServiceException(e, e.getStatusCode().value());
        }

        return responseEntity.getBody();
    }

    public Location getLocation(String locationAltKey) {
        HttpHeaders headers = new HttpHeaders();
        setHeaders(headers);

        // encode().build() has unexpected result, use build().encode()
        String locationService = UriComponentsBuilder.fromHttpUrl(gttBaseUrl + LOCATION_V_1_LOCATION)
                .queryParam(FORMAT, JSON)
                .queryParam(INLINECOUNT, ALLPAGES)
                .queryParam(FILTER, "locationAltKey eq '" + locationAltKey + "'").build().encode().toUriString();

        logger.debug("ready to call location service: {}", locationService);
        ResponseEntity<String> responseEntity;
        try {
            responseEntity = restTemplate.exchange(locationService, HttpMethod.GET,
                    new HttpEntity<>(null, headers), String.class);
        } catch (HttpStatusCodeException e) {
            logger.error("Call location service failed:", e);
            throw new LocationServiceException(e, e.getStatusCode().value());
        }

        ODataResultList<Location> res = ODataUtils.readEntitySet(responseEntity.getBody(), Location.class);

        return !res.getResults().isEmpty() ? res.getResults().get(0) : null;
    }

    public List<Location> getLocations(Set<String> locationAltKeys) {
        List<Location> result = new ArrayList<>();

        if(!locationAltKeys.isEmpty()) {
            HttpHeaders headers = new HttpHeaders();
            setHeaders(headers);
            List<FilterCondition> conditions = new ArrayList<>();
            locationAltKeys.forEach(locationAltKey ->
                    conditions.add(new FilterCondition("locationAltKey", FilterCondition.EDM_TYPE_STRING, locationAltKey, BinaryOperator.EQ)));
            FilterExpression filter = FilterExpressionBuilder.createFilterExpression(conditions, BinaryOperator.OR);
            assert filter != null;
            String locationService = UriComponentsBuilder.fromHttpUrl(gttBaseUrl + LOCATION_V_1_LOCATION)
                    .queryParam(FORMAT, JSON)
                    .queryParam(INLINECOUNT, ALLPAGES)
                    .queryParam(FILTER, filter.getExpressionString()).build().encode().toUriString();
            ResponseEntity<String> responseEntity;
            try {
                responseEntity = restTemplate
                    .exchange(locationService, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
            } catch(HttpStatusCodeException e) {
                logger.error("Call location service failed:", e);
                throw new LocationServiceException(e, e.getStatusCode().value());
            }
            result = ODataUtils.readEntitySet(responseEntity.getBody(), Location.class).getResults();
        }

        return result;
    }
}
