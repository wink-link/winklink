package com.tron.client;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.tron.client.message.BroadCastResponse;
import com.tron.client.message.EventData;
import com.tron.client.message.EventResponse;
import com.tron.client.message.TriggerResponse;
import com.tron.common.AbiUtil;
import com.tron.common.Config;
import com.tron.common.util.HttpUtil;
import com.tron.common.util.Tool;
import com.tron.job.JobSubscriber;
import com.tron.keystore.KeyStore;
import com.tron.web.entity.Head;
import com.tron.web.entity.TronTx;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import com.tron.web.service.HeadService;
import com.tron.web.service.JobRunsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.aspectj.weaver.ast.Or;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.JsonUtil;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.protos.Protocol;
import org.springframework.beans.factory.annotation.Autowired;
import com.beust.jcommander.internal.Sets;

import static com.tron.common.Constant.*;

/** Subscribe the events of the oracle contracts and reply. */
@Slf4j
@Component
public class OracleClient {
  private static HeadService headService;
  private static JobRunsService jobRunsService;

  @Autowired
  public OracleClient(HeadService headService, JobRunsService jobRunsService) {
    OracleClient.headService = headService;
    OracleClient.jobRunsService = jobRunsService;
  }

  private static final String EVENT_NAME = "OracleRequest";
  private static final String EVENT_NEW_ROUND = "NewRound";
  private static final String VRF_EVENT_NAME = "VRFRequest";

  private static final HashMap<String, String> initiatorEventMap =
      new HashMap<String, String>() {
        {
          put(INITIATOR_TYPE_RUN_LOG, EVENT_NAME + "," + EVENT_NEW_ROUND); // support multiple events for the same job
          put(INITIATOR_TYPE_RANDOMNESS_LOG, VRF_EVENT_NAME);
        }
      };

  private static Cache<String, String> requestIdsCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(12, TimeUnit.HOURS)
          .recordStats()
          .build();

  private static ConcurrentHashMap<String, Set<String>> listeningAddrs = new ConcurrentHashMap<>();
  private static HashMap<String, Long> consumeIndexMap = Maps.newHashMap();

  public static void init() {
    try {
      JobSubscriber.setup();
    } catch (Exception ex) {
      log.error("Exception in init: ", ex);
    }
  }

  private static void listenTask(String addr, String[] filterEvents)  {
    ScheduledExecutorService listenExecutor = Executors.newSingleThreadScheduledExecutor();
    listenExecutor.scheduleWithFixedDelay(
        () -> {
          try {
            listen(addr, filterEvents);
          } catch (Throwable t) {
            log.error("Exception in listener ", t);
          }
        },
        0,
        3000,
        TimeUnit.MILLISECONDS);
  }

  public static void registerJob(String address, String jobId, String initiatorType) {
    Set<String> set = listeningAddrs.get(address);
    if (set == null) {
      set = Sets.newHashSet();
    }
    set.add(jobId);
    if (listeningAddrs.get(address) == null) { // each address with only one listen task
      listeningAddrs.put(address, set);
      listenTask(address, initiatorEventMap.get(initiatorType).split(","));
    } else {
      listeningAddrs.put(address, set); // only add recent jobId
    }
  }

  /**
   * @param request
   * @return transactionid
   */
  public static void fulfil(FulfillRequest request, TronTx tx) throws Exception {
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", KeyStore.getAddr());
    params.put("contract_address", request.getContractAddr());
    params.put("function_selector", FULFIL_METHOD_SIGN);
    params.put("parameter", AbiUtil.parseParameters(FULFIL_METHOD_SIGN, request.toList()));
    params.put("fee_limit", Config.getMinFeeLimit());
    params.put("call_value", 0);
    params.put("visible", true);

    triggerSignAndResponse(params, tx);
  }

  /**
   * @param request
   * @return transactionid
   */
  public static void vrfFulfil(FulfillRequest request, TronTx vrfTx) throws Exception {
    List<Object> parameters = Arrays.asList(request.getData());
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", KeyStore.getAddr());
    params.put("contract_address", request.getContractAddr());
    params.put("function_selector", VRF_FULFIL_METHOD_SIGN);
    params.put("parameter", AbiUtil.parseParameters(VRF_FULFIL_METHOD_SIGN, parameters));
    params.put("fee_limit", Config.getMinFeeLimit());
    params.put("call_value", 0);
    params.put("visible", true);

    triggerSignAndResponse(params, vrfTx);
  }

  public static String convertWithIteration(Map<String, Object> map) {
    StringBuilder mapAsString = new StringBuilder("");
    for (String key : map.keySet()) {
      mapAsString.append(key + "=" + map.get(key) + ";");
    }
    mapAsString.delete(mapAsString.length()-1, mapAsString.length()).append("");
    return mapAsString.toString();
  }

  public static void triggerSignAndResponse(Map<String, Object> params, TronTx tx)
      throws Exception {
    String contractAddress = params.get("contract_address").toString();
    String data = convertWithIteration(params);
    tx.setFrom(KeyStore.getAddr());
    tx.setTo(contractAddress);
    tx.setData(data);

    String response =
        HttpUtil.post("https", FULLNODE_HOST, "/wallet/triggersmartcontract", params);
    TriggerResponse triggerResponse = null;
    triggerResponse = JsonUtil.json2Obj(response, TriggerResponse.class);

    tx.setSurrogateId(triggerResponse.getTransaction().getTxID());

    // sign
    ECKey key = KeyStore.getKey();
    String rawDataHex = triggerResponse.getTransaction().getRawDataHex();
    Protocol.Transaction.raw raw =
        Protocol.Transaction.raw.parseFrom(ByteArray.fromHexString(rawDataHex));
    byte[] hash = Sha256Hash.hash(true, raw.toByteArray());
    ECKey.ECDSASignature signature = key.sign(hash);
    ByteString bsSign = ByteString.copyFrom(signature.toByteArray());
    TransactionCapsule transactionCapsule = new TransactionCapsule(raw, Arrays.asList(bsSign));

    tx.setSignedRawTx(bsSign.toString());
    tx.setHash(ByteArray.toHexString(hash));

    // broadcast
    params.clear();
    params.put("transaction", Hex.toHexString(transactionCapsule.getInstance().toByteArray()));
    response = HttpUtil.post("https", FULLNODE_HOST,
            "/wallet/broadcasthex", params);
    BroadCastResponse broadCastResponse =
            JsonUtil.json2Obj(response, BroadCastResponse.class);
  }

  private static void listen(String addr, String[] filterEvents) {
     List<EventData> events = new ArrayList<>();
      for (String filterEvent : filterEvents) {
        List<EventData> data = getEventData(addr, filterEvent);
        if (data != null && data.size() >0 ) {
          events.addAll(data);
        }
      }
      if (events == null || events.size() == 0) {
        return;
      }
      // handle events
      for (EventData eventData : events) {
        log.info("Event received: {} | {}", eventData.getTransactionId(), eventData.getEventName());
        // update consumeIndexMap
        updateConsumeMap(addr, eventData.getBlockTimestamp());

        // filter the events
        String eventName = eventData.getEventName();
        switch (eventName) {
          case EVENT_NAME:
            processOracleRequestEvent(addr, eventData);
            break;
          case EVENT_NEW_ROUND:
            processNewRoundEvent(addr, eventData);
            break;
          case VRF_EVENT_NAME:
            processVrfRequestEvent(addr, eventData);
            break;
          default:
            log.warn("unexpected event:{}", eventName);
            break;
        }
      }

  }

  /** constructor. */
  public static String getBlockByNum(long blockNum) {
    try {
      Map<String, Object> params = Maps.newHashMap();
      params.put("num", blockNum);
      params.put("visible", true);
      String response =
          HttpUtil.post("https", FULLNODE_HOST, "/wallet/getblockbynum", params);

      return response;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private static void processOracleRequestEvent(String addr, EventData eventData) {
    String jobId = null;
    try {
      jobId = new String(
          org.apache.commons.codec.binary.Hex.decodeHex(
              ((String)eventData.getResult().get("specId"))));
    } catch (DecoderException e) {
      log.warn("parse job failed, jobid: {}", jobId);
      return;
    }
    // match jobId
    if (!listeningAddrs.get(addr).contains(jobId)) {
      log.warn("this node does not support this job, jobid: {}", jobId);
      return;
    }
    // Number/height of the block in which this request appeared
    long blockNum = eventData.getBlockNumber();
    String requester = Tool.convertHexToTronAddr((String)eventData.getResult().get("requester"));
    String callbackAddr = Tool.convertHexToTronAddr((String)eventData.getResult().get("callbackAddr"));
    String callbackFuncId = (String)eventData.getResult().get("callbackFunctionId");
    long cancelExpiration = Long.parseLong((String)eventData.getResult().get("cancelExpiration"));
    String data = (String)eventData.getResult().get("data");
    long dataVersion = Long.parseLong((String)eventData.getResult().get("dataVersion"));
    String requestId = (String)eventData.getResult().get("requestId");
    BigInteger payment = new BigInteger((String)eventData.getResult().get("payment"));
    if (requestIdsCache.getIfPresent(requestId) != null) {
      log.info("this event has been handled, requestid:{}", requestId);
      return;
    }
    JobSubscriber.receiveLogRequest(
        new EventRequest(blockNum, jobId, requester, callbackAddr, callbackFuncId,
            cancelExpiration, data, dataVersion,requestId, payment, addr));
    requestIdsCache.put(requestId, "");
  }

  private static void processVrfRequestEvent(String addr, EventData eventData) {
    String jobId = null;
    try {
      jobId = new String(
          org.apache.commons.codec.binary.Hex.decodeHex(
              ((String)eventData.getResult().get("jobID"))));
    } catch (DecoderException e) {
      log.warn("parse vrf job failed, jobid: {}", jobId);
      return;
    }
    // match jobId
    if (!listeningAddrs.get(addr).contains(jobId)) {
      log.warn("this node does not support this vrf job, jobid: {}", jobId);
      return;
    }
    // Number/height of the block in which this request appeared
    long blockNum = eventData.getBlockNumber();
    String requestId = (String) eventData.getResult().get("requestID");
    if (requestIdsCache.getIfPresent(requestId) != null) {
      log.info("this vrf event has been handled, requestid:{}", requestId);
      return;
    }
    if(!Strings.isNullOrEmpty(jobRunsService.getByRequestId(requestId))) { // for reboot
      log.info("from DB, this vrf event has been handled, requestid:{}", requestId);
      return;
    }
    // Hash of the block in which this request appeared
    String responseStr = getBlockByNum(blockNum);
    JSONObject responseContent = JSONObject.parseObject(responseStr);
    String blockHash = responseContent.getString("blockID");
    JSONObject rawHead = JSONObject.parseObject(JSONObject.parseObject(responseContent.getString("block_header"))
        .getString("raw_data"));
    String parentHash = rawHead.getString("parentHash");
    Long blockTimestamp = Long.valueOf(rawHead.getString("timestamp"));

    String sender = Tool.convertHexToTronAddr((String) eventData.getResult().get("sender"));
    String keyHash = (String) eventData.getResult().get("keyHash");
    String seed = (String) eventData.getResult().get("seed");
    BigInteger fee = new BigInteger((String) eventData.getResult().get("fee"));
    JobSubscriber.receiveVrfRequest(
        new VrfEventRequest(
            blockNum, blockHash, jobId, keyHash, seed, sender, requestId, fee, addr));
    requestIdsCache.put(requestId, "");

    List<Head> hisHead = headService.getByAddress(addr);
    Head head = new Head();
    head.setAddress(addr);
    head.setNumber(blockNum);
    head.setHash(blockHash);
    head.setParentHash(parentHash);
    head.setBlockTimestamp(blockTimestamp);
    if (hisHead == null || hisHead.size() == 0) {
      headService.insert(head);
    } else if (!hisHead.get(0).getNumber().equals(blockNum)) { //Only update unequal blockNum.
      head.setId(hisHead.get(0).getId());
      head.setUpdatedAt(new Date());
      headService.update(head);
    } else {

    }
  }

  private static void processNewRoundEvent(String addr, EventData eventData) {
    long roundId = 0;
    try {
      roundId = Long.parseLong((String)eventData.getResult().get("roundId"));
    } catch (NumberFormatException e) {
      log.warn("parse job failed, roundId: {}", roundId);
      return;
    }

    String startedBy = Tool.convertHexToTronAddr((String)eventData.getResult().get("startedBy"));
    long startedAt = Long.parseLong((String)eventData.getResult().get("startedAt"));
    if (requestIdsCache.getIfPresent(addr + roundId) != null) {
      log.info("this event has been handled, address:{}, roundId:{}", addr, roundId);
      return;
    }

    JobSubscriber.receiveNewRoundLog(addr, startedBy, roundId, startedAt);

    requestIdsCache.put(addr + roundId, "");
  }

  public static String requestEvent(String urlPath, Map<String, String> params) throws IOException {
    String response = HttpUtil.get("https", HTTP_EVENT_HOST,
        urlPath, params);
    if (Strings.isNullOrEmpty(response)) {
      int retry = 1;
      for (;;) {
        if(retry > HTTP_MAX_RETRY_TIME) {
          break;
        }
        try {
          Thread.sleep(100 * retry);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        response = HttpUtil.get("https", HTTP_EVENT_HOST,
            urlPath, params);
        retry++;
        if (!Strings.isNullOrEmpty(response)) {
          break;
        }
      }
    }
    return response;
  }

  private static List<EventData> getEventData(String addr, String filterEvent) {
    List<EventData> data = new ArrayList<>();
    String httpResponse = null;
    String urlPath;
    Map<String, String> params = Maps.newHashMap();
    if ("nile.trongrid.io".equals(HTTP_EVENT_HOST)) { // for test
      params.put("event_name", filterEvent);
      params.put("order_by", "block_timestamp,asc");
      if(!getMinBlockTimestamp(addr, filterEvent, params)){
        return null;
      }
      urlPath = String.format("/v1/contracts/%s/events", addr);
    } else { // for production
      params.put("event_name", filterEvent);
      params.put("order_by", "block_timestamp,asc");
      // params.put("only_confirmed", "true");
      if(!getMinBlockTimestamp(addr, filterEvent, params)){
        return null;
      }
      urlPath = String.format("/v1/contracts/%s/events", addr);
    }
    EventResponse response = null;
    try {
      httpResponse = requestEvent(urlPath, params);
      if (Strings.isNullOrEmpty(httpResponse)) {
        return null;
      }
      response = JsonUtil.json2Obj(httpResponse, EventResponse.class);
    } catch (IOException e) {
      log.error("parse response failed, err: {}", e.getMessage());
      return data;
    }
    data.addAll(response.getData());

    boolean isNext = false;
    Map<String, String> links = response.getMeta().getLinks();
    if (links == null) {
      return data;
    }
    String urlNext = links.get("next");
    if (!Strings.isNullOrEmpty(urlNext)) {
      isNext = true;
    }
    while (isNext) {
      isNext = false;
      String responseNext = requestNextPage(urlNext);
      if (Strings.isNullOrEmpty(responseNext)) {
        return data;
      }
      try {
        response = JsonUtil.json2Obj(responseNext, EventResponse.class);
      } catch (Exception e) {
        log.error("parse response failed, err: {}", e.getMessage());
        return data;
      }
      data.addAll(response.getData());

      links = response.getMeta().getLinks();
      if (links == null) {
        return data;
      }
      urlNext = links.get("next");
      if (!Strings.isNullOrEmpty(urlNext)) {
        isNext = true;
      }
    }

    return data;
  }
  public static String requestNextPage(String urlNext) {
    try {
      String response = HttpUtil.requestWithRetry(urlNext);
      if (response == null) {
        return null;
      }
      return response;
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return null;
  }



  private static void updateConsumeMap(String addr, long timestamp) {
    if (consumeIndexMap.containsKey(addr)) {
      if (timestamp > consumeIndexMap.get(addr)) {
        consumeIndexMap.put(addr, timestamp);
      }
    } else {
      consumeIndexMap.put(addr, timestamp);
    }
    log.info("Update consumeIndexMap: {} | {}", addr, timestamp);
  }

  public static boolean getMinBlockTimestamp(String addr, String eventName, Map<String, String> params)
  {
    switch (eventName) {
      case EVENT_NAME:
      case EVENT_NEW_ROUND:
        if (consumeIndexMap.containsKey(addr)) {
          params.put("min_block_timestamp", Long.toString(consumeIndexMap.get(addr)));
        } else {
          params.put("min_block_timestamp", Long.toString(System.currentTimeMillis() - ONE_MINUTE));
        }
        break;
      case VRF_EVENT_NAME:
        if (consumeIndexMap.containsKey(addr)) {
          params.put("min_block_timestamp", Long.toString(consumeIndexMap.get(addr)));
        } else {
          List<Head> hisHead = headService.getByAddress(addr);
          if (hisHead == null || hisHead.size() == 0) {
            params.put("min_block_timestamp", Long.toString(System.currentTimeMillis() - ONE_MINUTE));
          } else {
            params.put("min_block_timestamp", Long.toString(hisHead.get(0).getBlockTimestamp()));
          }
        }
        break;
      default:
        log.warn("unexpected event:{}", eventName);
        return false;
    }
    return true;
  }
}
