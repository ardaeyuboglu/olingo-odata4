/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.olingo.client.core.it.v4;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpResponse;
import org.apache.olingo.client.api.ODataBatchConstants;
import org.apache.olingo.client.api.communication.header.HeaderName;
import org.apache.olingo.client.api.communication.header.ODataPreferences;
import org.apache.olingo.client.api.communication.request.ODataStreamManager;
import org.apache.olingo.client.api.communication.request.batch.ODataBatchResponseItem;
import org.apache.olingo.client.api.communication.request.batch.ODataChangeset;
import org.apache.olingo.client.api.communication.request.batch.ODataRetrieve;
import org.apache.olingo.client.api.communication.request.batch.v4.BatchStreamManager;
import org.apache.olingo.client.api.communication.request.batch.v4.ODataBatchRequest;
import org.apache.olingo.client.api.communication.request.batch.v4.ODataOutsideUpdate;
import org.apache.olingo.client.api.communication.request.cud.ODataEntityCreateRequest;
import org.apache.olingo.client.api.communication.request.cud.ODataEntityUpdateRequest;
import org.apache.olingo.client.api.communication.request.cud.v4.UpdateType;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntityRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetRequest;
import org.apache.olingo.client.api.communication.response.ODataBatchResponse;
import org.apache.olingo.client.api.communication.response.ODataEntityCreateResponse;
import org.apache.olingo.client.api.communication.response.ODataEntityUpdateResponse;
import org.apache.olingo.client.api.communication.response.ODataResponse;
import org.apache.olingo.client.api.uri.v4.URIBuilder;
import org.apache.olingo.client.core.communication.request.AbstractODataStreamManager;
import org.apache.olingo.client.core.communication.request.Wrapper;
import org.apache.olingo.client.core.communication.request.batch.ODataChangesetResponseItem;
import org.apache.olingo.client.core.communication.request.batch.ODataRetrieveResponseItem;
import org.apache.olingo.client.core.communication.request.batch.v4.ODataOutsideUpdateResponseItem;
import org.apache.olingo.client.core.communication.request.retrieve.ODataEntityRequestImpl;
import org.apache.olingo.client.core.communication.request.retrieve.ODataEntityRequestImpl.ODataEntityResponseImpl;
import org.apache.olingo.client.core.uri.URIUtils;
import org.apache.olingo.commons.api.domain.v4.ODataEntity;
import org.apache.olingo.commons.api.domain.v4.ODataEntitySet;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeException;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.format.ODataPubFormat;
import org.junit.Test;

public class BatchTestITCase extends AbstractTestITCase {

  private static final String PREFIX = "!!PREFIX!!";

  private static final String SUFFIX = "!!SUFFIX!!";

  private static final int MAX = 10000;

  // ------------------------
  // Uncomment to performe check externally ...
  // ------------------------
  // private final static String testStaticServiceRootURL= 
  //                  "http://odatae2etest.azurewebsites.net/javatest/DefaultService/";
  // private final static String ACCEPT = ContentType.MULTIPART_MIXED;
  // ------------------------
  private final static String ACCEPT = ContentType.APPLICATION_OCTET_STREAM;

  @Test
  public void stringStreaming() {
    final TestStreamManager streaming = new TestStreamManager();

    new StreamingThread(streaming).start();

    streaming.addObject((PREFIX + "\n").getBytes());

    for (int i = 0; i <= MAX; i++) {
      streaming.addObject((i + ") send info\n").getBytes());
    }

    streaming.addObject(SUFFIX.getBytes());
    streaming.finalizeBody();
  }

  @Test
  public void emptyBatchRequest() {
    // create your request
    final ODataBatchRequest request = client.getBatchRequestFactory().getBatchRequest(testStaticServiceRootURL);
    request.setAccept(ACCEPT);

    final BatchStreamManager payload = request.execute();
    final ODataBatchResponse response = payload.getResponse();

    assertEquals(200, response.getStatusCode());
    assertEquals("OK", response.getStatusMessage());

    final Iterator<ODataBatchResponseItem> iter = response.getBody();
    assertFalse(iter.hasNext());
  }

  @Test
  public void changesetWithError() {
    // create your request
    final ODataBatchRequest request = client.getBatchRequestFactory().getBatchRequest(testStaticServiceRootURL);
    request.setAccept(ACCEPT);

    final BatchStreamManager payload = request.execute();
    final ODataChangeset changeset = payload.addChangeset();

    URIBuilder targetURI;
    ODataEntityCreateRequest<ODataEntity> createReq;

    targetURI = client.getURIBuilder(testStaticServiceRootURL).appendEntitySetSegment("Orders");
    for (int i = 1; i <= 2; i++) {
      // Create Customer into the changeset
      createReq = client.getCUDRequestFactory().getEntityCreateRequest(targetURI.build(), newOrder(100 + i));
      createReq.setFormat(ODataPubFormat.JSON);
      changeset.addRequest(createReq);
    }

    targetURI = client.getURIBuilder(testStaticServiceRootURL).appendEntitySetSegment("WrongEntitySet");
    createReq = client.getCUDRequestFactory().getEntityCreateRequest(targetURI.build(), newOrder(105));
    createReq.setFormat(ODataPubFormat.JSON);
    changeset.addRequest(createReq);

    targetURI = client.getURIBuilder(testStaticServiceRootURL).appendEntitySetSegment("Orders");
    for (int i = 3; i <= 4; i++) {
      // Create Customer into the changeset
      createReq = client.getCUDRequestFactory().getEntityCreateRequest(targetURI.build(), newOrder(100 + i));
      createReq.setFormat(ODataPubFormat.ATOM);
      changeset.addRequest(createReq);
    }

    final ODataBatchResponse response = payload.getResponse();
    assertEquals(200, response.getStatusCode());
    assertEquals("OK", response.getStatusMessage());

    // retrieve the first item (ODataRetrieve)
    ODataBatchResponseItem item = response.getBody().next();

    ODataChangesetResponseItem retitem = (ODataChangesetResponseItem) item;
    ODataResponse res = retitem.next();
    assertEquals(404, res.getStatusCode());
    assertEquals("Not Found", res.getStatusMessage());
    assertEquals(Integer.valueOf(3), Integer.valueOf(
            res.getHeader(ODataBatchConstants.CHANGESET_CONTENT_ID_NAME).iterator().next()));
  }

  @Test
  public void continueOnError() {
    continueOnError(true);
  }

  @Test
  public void doNotContinueOnError() {
    continueOnError(false);
  }

  private void continueOnError(final boolean continueOnError) {
    // create your request
    final ODataBatchRequest request = client.getBatchRequestFactory().getBatchRequest(testStaticServiceRootURL);
    request.setAccept(ACCEPT);

    if (continueOnError) {
      request.addCustomHeader(HeaderName.prefer, new ODataPreferences(client.getServiceVersion()).continueOnError());
    }

    final BatchStreamManager streamManager = request.execute();

    // -------------------------------------------
    // Add retrieve item
    // -------------------------------------------
    ODataRetrieve retrieve = streamManager.addRetrieve();

    // prepare URI
    URIBuilder targetURI = client.getURIBuilder(testStaticServiceRootURL);
    targetURI.appendEntitySetSegment("UnexistinfEntitySet").appendKeySegment(1);

    // create new request
    ODataEntityRequest<ODataEntity> queryReq = client.getRetrieveRequestFactory().getEntityRequest(targetURI.build());
    queryReq.setFormat(ODataPubFormat.ATOM);

    retrieve.setRequest(queryReq);
    // -------------------------------------------

    // -------------------------------------------
    // Add retrieve item
    // -------------------------------------------
    retrieve = streamManager.addRetrieve();

    // prepare URI
    targetURI = client.getURIBuilder(testStaticServiceRootURL).appendEntitySetSegment("Customers").appendKeySegment(1);

    // create new request
    queryReq = client.getRetrieveRequestFactory().getEntityRequest(targetURI.build());

    retrieve.setRequest(queryReq);
    // -------------------------------------------

    final ODataBatchResponse response = streamManager.getResponse();
    assertEquals(200, response.getStatusCode());
    assertEquals("OK", response.getStatusMessage());
    final Iterator<ODataBatchResponseItem> iter = response.getBody();

    // retrieve the first item (ODataRetrieve)
    ODataBatchResponseItem item = iter.next();
    assertTrue(item instanceof ODataRetrieveResponseItem);

    ODataRetrieveResponseItem retitem = (ODataRetrieveResponseItem) item;
    ODataResponse res = retitem.next();
    assertEquals(404, res.getStatusCode());
    assertEquals("Not Found", res.getStatusMessage());

    if (continueOnError) {
      item = iter.next();
      assertTrue(item instanceof ODataRetrieveResponseItem);

      retitem = (ODataRetrieveResponseItem) item;
      res = retitem.next();
      assertTrue(res instanceof ODataEntityResponseImpl);
      assertEquals(200, res.getStatusCode());
      assertEquals("OK", res.getStatusMessage());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void changesetWithReference() throws EdmPrimitiveTypeException {
    // create your request
    final ODataBatchRequest request = client.getBatchRequestFactory().getBatchRequest(testStaticServiceRootURL);
    request.setAccept(ACCEPT);
    final BatchStreamManager streamManager = request.execute();

    final ODataChangeset changeset = streamManager.addChangeset();
    ODataEntity order = newOrder(20);

    final URIBuilder uriBuilder = client.getURIBuilder(testStaticServiceRootURL).appendEntitySetSegment("Orders");

    // add create request
    final ODataEntityCreateRequest<ODataEntity> createReq =
            client.getCUDRequestFactory().getEntityCreateRequest(uriBuilder.build(), order);

    changeset.addRequest(createReq);

    // retrieve request reference
    int createRequestRef = changeset.getLastContentId();

    // add update request: link CustomerInfo(17) to the new customer
    final ODataEntity customerChanges = client.getObjectFactory().newEntity(order.getTypeName());
    customerChanges.addLink(client.getObjectFactory().newEntitySetNavigationLink(
            "OrderDetails",
            client.getURIBuilder(testStaticServiceRootURL).appendEntitySetSegment("OrderDetails").
            appendKeySegment(new HashMap<String, Object>() {
      private static final long serialVersionUID = 3109256773218160485L;

      {
        put("OrderID", 7);
        put("ProductID", 5);
      }
    }).build()));

    final ODataEntityUpdateRequest<ODataEntity> updateReq = client.getCUDRequestFactory().getEntityUpdateRequest(
            URI.create("$" + createRequestRef), UpdateType.PATCH, customerChanges);

    changeset.addRequest(updateReq);

    final ODataBatchResponse response = streamManager.getResponse();
    assertEquals(200, response.getStatusCode());
    assertEquals("OK", response.getStatusMessage());

    // verify response payload ...
    final Iterator<ODataBatchResponseItem> iter = response.getBody();

    final ODataBatchResponseItem item = iter.next();
    assertTrue(item instanceof ODataChangesetResponseItem);

    final ODataChangesetResponseItem chgitem = (ODataChangesetResponseItem) item;

    ODataResponse res = chgitem.next();
    assertEquals(201, res.getStatusCode());
    assertTrue(res instanceof ODataEntityCreateResponse);

    order = ((ODataEntityCreateResponse<ODataEntity>) res).getBody();
    final ODataEntitySetRequest<ODataEntitySet> req = client.getRetrieveRequestFactory().getEntitySetRequest(
            URIUtils.getURI(testStaticServiceRootURL, order.getEditLink().toASCIIString() + "/OrderDetails"));

    assertEquals(Integer.valueOf(7),
            req.execute().getBody().getEntities().get(0).getProperty("OrderID").getPrimitiveValue().
            toCastValue(Integer.class));

    res = chgitem.next();
    assertEquals(204, res.getStatusCode());
    assertTrue(res instanceof ODataEntityUpdateResponse);

    // clean ...
    assertEquals(204, client.getCUDRequestFactory().getDeleteRequest(
            URIUtils.getURI(testStaticServiceRootURL, order.getEditLink().toASCIIString())).execute().
            getStatusCode());

    try {
      client.getRetrieveRequestFactory().getEntityRequest(
              URIUtils.getURI(testStaticServiceRootURL, order.getEditLink().toASCIIString())).
              execute().getBody();
      fail();
    } catch (Exception e) {
      // ignore
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void batchRequestWithOutsideUpdates() throws EdmPrimitiveTypeException {
    // create your request
    final ODataBatchRequest request = client.getBatchRequestFactory().getBatchRequest(testStaticServiceRootURL);
    request.setAccept(ACCEPT);
    final BatchStreamManager streamManager = request.execute();

    // -------------------------------------------
    // Add retrieve item
    // -------------------------------------------
    ODataRetrieve retrieve = streamManager.addRetrieve();

    // prepare URI
    URIBuilder targetURI = client.getURIBuilder(testStaticServiceRootURL);
    targetURI.appendEntitySetSegment("Customers").appendKeySegment(1).
            expand("Orders").select("PersonID,Orders/OrderID");

    // create new request
    ODataEntityRequest<ODataEntity> queryReq = client.getRetrieveRequestFactory().getEntityRequest(targetURI.build());
    queryReq.setFormat(ODataPubFormat.ATOM);

    retrieve.setRequest(queryReq);
    // -------------------------------------------

    // -------------------------------------------
    // Add new order with outside item
    // -------------------------------------------
    final ODataOutsideUpdate outside = streamManager.addOutsideUpdate();

    // prepare URI
    targetURI = client.getURIBuilder(testStaticServiceRootURL).appendEntitySetSegment("Orders");
    final ODataEntity original = newOrder(2000);
    final ODataEntityCreateRequest<ODataEntity> createReq =
            client.getCUDRequestFactory().getEntityCreateRequest(targetURI.build(), original);
    createReq.setFormat(ODataPubFormat.ATOM);
    outside.setRequest(createReq);
    // -------------------------------------------

    final ODataBatchResponse response = streamManager.getResponse();
    assertEquals(200, response.getStatusCode());
    assertEquals("OK", response.getStatusMessage());
    final Iterator<ODataBatchResponseItem> iter = response.getBody();

    // retrieve the first item (ODataRetrieve)
    ODataBatchResponseItem item = iter.next();
    assertTrue(item instanceof ODataRetrieveResponseItem);

    ODataRetrieveResponseItem retitem = (ODataRetrieveResponseItem) item;
    ODataResponse res = retitem.next();
    assertTrue(res instanceof ODataEntityResponseImpl);
    assertEquals(200, res.getStatusCode());
    assertEquals("OK", res.getStatusMessage());

    // retrieve the second item (ODataChangeset)
    item = iter.next();
    assertTrue(item instanceof ODataOutsideUpdateResponseItem);

    final ODataOutsideUpdateResponseItem outitem = (ODataOutsideUpdateResponseItem) item;
    res = outitem.next();
    assertTrue(res instanceof ODataEntityCreateResponse);
    assertEquals(201, res.getStatusCode());
    assertEquals("Created", res.getStatusMessage());

    final ODataEntityCreateResponse<ODataEntity> entres = (ODataEntityCreateResponse<ODataEntity>) res;
    final ODataEntity entity = entres.getBody();
    assertEquals(2000, entity.getProperty("OrderID").getPrimitiveValue().toCastValue(Integer.class).intValue());

    assertFalse(iter.hasNext());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void batchRequest() throws EdmPrimitiveTypeException {
    // create your request
    final ODataBatchRequest request = client.getBatchRequestFactory().getBatchRequest(testStaticServiceRootURL);
    request.setAccept(ACCEPT);

    final BatchStreamManager streamManager = request.execute();

    // -------------------------------------------
    // Add retrieve item
    // -------------------------------------------
    ODataRetrieve retrieve = streamManager.addRetrieve();

    // prepare URI
    URIBuilder targetURI = client.getURIBuilder(testStaticServiceRootURL);
    targetURI.appendEntitySetSegment("Customers").appendKeySegment(1).
            expand("Orders").select("PersonID,Orders/OrderID");

    // create new request
    ODataEntityRequest<ODataEntity> queryReq = client.getRetrieveRequestFactory().getEntityRequest(targetURI.build());
    queryReq.setFormat(ODataPubFormat.ATOM);

    retrieve.setRequest(queryReq);
    // -------------------------------------------

    // -------------------------------------------
    // Add changeset item
    // -------------------------------------------
    final ODataChangeset changeset = streamManager.addChangeset();

    // Update Customer into the changeset
    targetURI = client.getURIBuilder(testStaticServiceRootURL).appendEntitySetSegment("Customers").appendKeySegment(1);
    final URI editLink = targetURI.build();

    final ODataEntity patch = client.getObjectFactory().newEntity(
            new FullQualifiedName("Microsoft.Test.OData.Services.ODataWCFService.Customer"));
    patch.setEditLink(editLink);

    patch.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
            "LastName",
            client.getObjectFactory().newPrimitiveValueBuilder().buildString("new last name")));

    final ODataEntityUpdateRequest<ODataEntity> changeReq =
            client.getCUDRequestFactory().getEntityUpdateRequest(UpdateType.PATCH, patch);
    changeReq.setFormat(ODataPubFormat.JSON_FULL_METADATA);

    changeset.addRequest(changeReq);

    // Create Order into the changeset
    targetURI = client.getURIBuilder(testStaticServiceRootURL).appendEntitySetSegment("Orders");
    final ODataEntity original = newOrder(1000);
    final ODataEntityCreateRequest<ODataEntity> createReq =
            client.getCUDRequestFactory().getEntityCreateRequest(targetURI.build(), original);
    createReq.setFormat(ODataPubFormat.ATOM);
    changeset.addRequest(createReq);
    // -------------------------------------------

    // -------------------------------------------
    // Add retrieve item
    // -------------------------------------------
    retrieve = streamManager.addRetrieve();

    // prepare URI
    targetURI = client.getURIBuilder(testStaticServiceRootURL).appendEntitySetSegment("Customers").appendKeySegment(1);

    // create new request
    queryReq = client.getRetrieveRequestFactory().getEntityRequest(targetURI.build());

    retrieve.setRequest(queryReq);
    // -------------------------------------------

    final ODataBatchResponse response = streamManager.getResponse();
    assertEquals(200, response.getStatusCode());
    assertEquals("OK", response.getStatusMessage());
    final Iterator<ODataBatchResponseItem> iter = response.getBody();

    // retrieve the first item (ODataRetrieve)
    ODataBatchResponseItem item = iter.next();
    assertTrue(item instanceof ODataRetrieveResponseItem);

    ODataRetrieveResponseItem retitem = (ODataRetrieveResponseItem) item;
    ODataResponse res = retitem.next();
    assertTrue(res instanceof ODataEntityResponseImpl);
    assertEquals(200, res.getStatusCode());
    assertEquals("OK", res.getStatusMessage());

    ODataEntityRequestImpl<ODataEntity>.ODataEntityResponseImpl entres =
            (ODataEntityRequestImpl.ODataEntityResponseImpl) res;

    ODataEntity entity = entres.getBody();
    assertEquals(1, entity.getProperty("PersonID").getPrimitiveValue().toCastValue(Integer.class), 0);

    // retrieve the second item (ODataChangeset)
    item = iter.next();
    assertTrue(item instanceof ODataChangesetResponseItem);

    final ODataChangesetResponseItem chgitem = (ODataChangesetResponseItem) item;
    res = chgitem.next();
    assertTrue(res instanceof ODataEntityUpdateResponse);
    assertEquals(204, res.getStatusCode());
    assertEquals("No Content", res.getStatusMessage());

    res = chgitem.next();
    assertTrue(res instanceof ODataEntityCreateResponse);
    assertEquals(201, res.getStatusCode());
    assertEquals("Created", res.getStatusMessage());

    final ODataEntityCreateResponse<ODataEntity> createres = (ODataEntityCreateResponse<ODataEntity>) res;
    entity = createres.getBody();
    assertEquals(new Integer(1000), entity.getProperty("OrderID").getPrimitiveValue().toCastValue(Integer.class));

    // retrive the third item (ODataRetrieve)
    item = iter.next();
    assertTrue(item instanceof ODataRetrieveResponseItem);

    retitem = (ODataRetrieveResponseItem) item;
    res = retitem.next();
    assertTrue(res instanceof ODataEntityResponseImpl);
    assertEquals(200, res.getStatusCode());
    assertEquals("OK", res.getStatusMessage());

    entres = (ODataEntityRequestImpl.ODataEntityResponseImpl) res;
    entity = entres.getBody();
    assertEquals("new last name",
            entity.getProperty("LastName").getPrimitiveValue().toCastValue(String.class));

    assertFalse(iter.hasNext());
  }

  private static class TestStreamManager extends AbstractODataStreamManager<ODataBatchResponse> {

    public TestStreamManager() {
      super(new Wrapper<Future<HttpResponse>>());
    }

    public ODataStreamManager<ODataBatchResponse> addObject(final byte[] src) {
      stream(src);
      return this;
    }

    @Override
    protected ODataBatchResponse getResponse(final long timeout, final TimeUnit unit) {
      throw new UnsupportedOperationException("Not supported yet.");
    }
  };

  /**
   * To be used for debug purposes.
   */
  private static class StreamingThread extends Thread {

    private final TestStreamManager streaming;

    public StreamingThread(final TestStreamManager streaming) {
      super();
      this.streaming = streaming;
    }

    @Override
    public void run() {
      try {
        final StringBuilder builder = new StringBuilder();

        byte[] buff = new byte[1024];

        int len;

        while ((len = streaming.getBody().read(buff)) >= 0) {
          builder.append(new String(buff, 0, len));
        }

        assertTrue(builder.toString().startsWith(PREFIX));
        assertTrue(builder.toString().contains((MAX / 2) + ") send info"));
        assertTrue(builder.toString().contains((MAX / 3) + ") send info"));
        assertTrue(builder.toString().contains((MAX / 20) + ") send info"));
        assertTrue(builder.toString().contains((MAX / 30) + ") send info"));
        assertTrue(builder.toString().contains(MAX + ") send info"));
        assertTrue(builder.toString().endsWith(SUFFIX));

      } catch (IOException e) {
        fail();
      }
    }
  }

  private static class BatchStreamingThread extends Thread {

    private final BatchStreamManager streaming;

    public BatchStreamingThread(final BatchStreamManager streaming) {
      super();
      this.streaming = streaming;
    }

    @Override
    public void run() {
      try {
        final StringBuilder builder = new StringBuilder();

        final byte[] buff = new byte[1024];

        int len;

        while ((len = streaming.getBody().read(buff)) >= 0) {
          builder.append(new String(buff, 0, len));
        }

        LOG.debug("Batch request {}", builder.toString());

        assertTrue(builder.toString().contains("Content-Id:2"));
        assertTrue(builder.toString().contains("GET " + testStaticServiceRootURL));
      } catch (IOException e) {
        fail();
      }
    }
  }

  private ODataEntity newOrder(final int id) {
    final ODataEntity order = getClient().getObjectFactory().
            newEntity(new FullQualifiedName("Microsoft.Test.OData.Services.ODataWCFService.Order"));

    order.getProperties().add(getClient().getObjectFactory().newPrimitiveProperty("OrderID",
            getClient().getObjectFactory().newPrimitiveValueBuilder().buildInt32(id)));
    order.getProperties().add(getClient().getObjectFactory().newPrimitiveProperty("OrderDate",
            getClient().getObjectFactory().newPrimitiveValueBuilder().
            setType(EdmPrimitiveTypeKind.DateTimeOffset).setValue(Calendar.getInstance()).build()));
    order.getProperties().add(getClient().getObjectFactory().newPrimitiveProperty("ShelfLife",
            getClient().getObjectFactory().newPrimitiveValueBuilder().
            setType(EdmPrimitiveTypeKind.Duration).setText("PT0.0000002S").build()));

    return order;
  }
}
