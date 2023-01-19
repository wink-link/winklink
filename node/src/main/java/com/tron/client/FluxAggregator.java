package com.tron.client;

import static com.tron.common.Constant.FULFIL_METHOD_SIGN;
import static com.tron.common.Constant.FULLNODE_HOST;
import static com.tron.common.Constant.ROUND_STATE_METHOD_SIGN;
import static com.tron.common.Constant.ROUND_STATE_RESULT_SIGN;
import static com.tron.common.Constant.SUBMIT_METHOD_SIGN;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.tron.client.message.BroadCastResponse;
import com.tron.client.message.OracleRoundState;
import com.tron.client.message.TriggerResponse;
import com.tron.common.AbiUtil;
import com.tron.common.Config;
import com.tron.common.ContractDecoder;
import com.tron.common.util.HttpUtil;
import com.tron.keystore.KeyStore;
import com.tron.web.entity.TronTx;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Or;
import org.bouncycastle.util.encoders.Hex;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.JsonUtil;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.protos.Protocol;
import org.tron.trident.abi.datatypes.Bool;
import org.tron.trident.abi.datatypes.Int;
import org.tron.trident.abi.datatypes.Type;
import org.tron.trident.abi.datatypes.Uint;

@Slf4j
public class FluxAggregator {

  /**
   *
   * @param
   * @return transactionid
   */
  public static void submit(String addr, long roundId, long result, TronTx tx)
      throws Exception {
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", KeyStore.getAddr());
    params.put("contract_address", addr);
    params.put("function_selector", SUBMIT_METHOD_SIGN);
    List<Object> list = Lists.newArrayList();
    list.add(roundId);
    list.add(result);
    params.put("parameter", AbiUtil.parseParameters(SUBMIT_METHOD_SIGN, list));
    params.put("fee_limit", Config.getMinFeeLimit());
    params.put("call_value",0);
    params.put("visible",true);

    OracleClient.triggerSignAndResponse(params, tx);
  }

  public static OracleRoundState getOracleRoundState(String addr, long roundId) {
    OracleRoundState oracleRoundState = null;
    try {
      Map<String, Object> params = Maps.newHashMap();
      params.put("owner_address", KeyStore.getAddr());
      params.put("contract_address", addr);
      params.put("function_selector", ROUND_STATE_METHOD_SIGN);
      List<Object> list = Lists.newArrayList();
      list.add(KeyStore.getAddr());
      list.add(roundId);
      params.put("parameter", AbiUtil.parseParameters(ROUND_STATE_METHOD_SIGN, list));
      params.put("visible",true);

      String response = HttpUtil.post("https", FULLNODE_HOST,
          "/wallet/triggersmartcontract", params);
      ObjectMapper mapper = new ObjectMapper();
      assert response != null;
      Map<String, Object> result = mapper.readValue(response, Map.class);

      // decode result
      List<Type> ret =  ContractDecoder.decode(ROUND_STATE_RESULT_SIGN, ((List<String>)result.get("constant_result")).get(0));
      oracleRoundState = new OracleRoundState();
      oracleRoundState.setEligibleToSubmit(((Bool)ret.get(0)).getValue());
      oracleRoundState.setRoundId(((Uint)ret.get(1)).getValue().longValue());
      oracleRoundState.setLatestSubmission(((Int)ret.get(2)).getValue());
      oracleRoundState.setStartedAt(((Uint)ret.get(3)).getValue().longValue());
      oracleRoundState.setTimeout(((Uint)ret.get(4)).getValue().longValue());
      oracleRoundState.setAvailableFunds(((Uint)ret.get(5)).getValue());
      oracleRoundState.setOracleCount(((Uint)ret.get(6)).getValue().intValue());
      oracleRoundState.setPaymentAmount(((Uint)ret.get(7)).getValue());
    } catch (Exception e) {
      log.error("get oracle round state info error, msg:" + e.getMessage());
    }

    return oracleRoundState;
  }

  public static boolean checkOracleRoundState(OracleRoundState oracleRoundState) {
    System.out.println(oracleRoundState);
    if (oracleRoundState == null) {
      return false;
    }

    if (!oracleRoundState.getEligibleToSubmit()) {
      log.warn("not eligible to submit");
      return false;
    }

    if (oracleRoundState.getPaymentAmount() == null ||
        oracleRoundState.getPaymentAmount().compareTo(BigInteger.ZERO) == 0) {
      log.warn("PaymentAmount shouldn't be 0");
      return false;
    }

    BigInteger minFunds = oracleRoundState.getPaymentAmount().multiply(
        new BigInteger(String.valueOf(oracleRoundState.getOracleCount() * 3)));
    if (oracleRoundState.getAvailableFunds() == null ||
        minFunds.compareTo(oracleRoundState.getAvailableFunds()) > 0) {
      log.warn("aggregator is underfunded");
      return false;
    }

    return true;
  }
}
