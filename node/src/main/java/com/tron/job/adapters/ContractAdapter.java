package com.tron.job.adapters;

import static com.tron.common.Constant.HTTP_EVENT_HOST;
import static com.tron.common.Constant.TRX_DECIMAL_STR;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.tron.common.Constant;
import com.tron.common.util.AbiUtil;
import com.tron.common.util.HttpUtil;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.util.encoders.Hex;
@Slf4j
public class ContractAdapter {

  private static final String TRONGRID_HOST = "api.trongrid.io";
  private static final String GET_ACCOUNT = "/walletsolidity/getaccount";
  private static final String TRIGGET_CONSTANT = "/wallet/triggerconstantcontract";
  private static final String READONLY_ACCOUNT = "TXmVpin5vq5gdZsciyyjdZgKRUju4st1wM";  // sun

  private static final String BALANCE_OF = "balanceOf(address)";
  private static final String DECIMAL = "decimals()";

  public static long getTRXBalance(String addr) throws Exception {
    return getTRXBalance(addr, true, false);
  }

  public static long getTRXBalance(String addr, boolean visible, boolean flexibleHost) throws Exception {
    Map<String, Object> params = Maps.newHashMap();
    params.put("address", addr);
    params.put("visible", visible);
    String response = null;
    if (flexibleHost) {
      response = HttpUtil.post("https", Constant.FULLNODE_HOST, GET_ACCOUNT, params);
      log.info("Get TRX balance - Request {} | Params {} | Response {}", "https://" + Constant.FULLNODE_HOST + GET_ACCOUNT, params, response);
    } else {
      response = HttpUtil.post("https", TRONGRID_HOST, GET_ACCOUNT, params);
      log.info("Get TRX balance - Request {} | Params {} | Response {}", "https://" + TRONGRID_HOST + GET_ACCOUNT, params, response);
    }
    ObjectMapper mapper = new ObjectMapper();
    assert response != null;
    Map<String, Object> result = mapper.readValue(response, Map.class);
    return Optional.ofNullable(result.get("balance"))
            .map(balance -> {
              if (balance instanceof Integer) {
                return ((Integer) balance).longValue();
              }
              return (long)balance;
            })
            .orElse(0L);
  }

  public static BigInteger balanceOf(String ownerAddress, String contractAddress) throws Exception {
    return balanceOf(ownerAddress, contractAddress, true);
  }

  public static BigInteger balanceOf(String ownerAddress, String contractAddress, boolean visible) throws Exception {
    if (!visible) {
      throw new UnsupportedOperationException("not supported yet");
    }
    String param = AbiUtil.parseParameters(BALANCE_OF, Arrays.asList(ownerAddress));
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", ownerAddress);
    params.put("contract_address", contractAddress);
    params.put("function_selector", BALANCE_OF);
    params.put("parameter", param);
    params.put("visible", visible);
    String response = HttpUtil.post(
            "https", TRONGRID_HOST, TRIGGET_CONSTANT, params);
    ObjectMapper mapper = new ObjectMapper();
    assert response != null;
    Map<String, Object> result = mapper.readValue(response, Map.class);
    return Optional.ofNullable((List<String>)result.get("constant_result"))
            .map(constantResult -> constantResult.get(0))
            .map(Hex::decode)
            .map(Hex::toHexString)
            .map(str -> new BigInteger(str, 16))
            .orElse(new BigInteger("0"));
  }

  public static int getDecimal(String contractAddress) throws Exception {
    return getDecimal(contractAddress, true);
  }

  public static int getDecimal(String contractAddress, boolean visible) throws Exception {
    if (!visible) {
      throw new UnsupportedOperationException("not supported yet");
    }
    String param = AbiUtil.parseParameters(DECIMAL, "");
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", READONLY_ACCOUNT);
    params.put("contract_address", contractAddress);
    params.put("function_selector", DECIMAL);
    params.put("parameter", param);
    params.put("visible", visible);
    String response = HttpUtil.post(
            "https", TRONGRID_HOST, TRIGGET_CONSTANT, params);
    ObjectMapper mapper = new ObjectMapper();
    assert response != null;
    Map<String, Object> result = mapper.readValue(response, Map.class);
    return Optional.ofNullable((List<String>)result.get("constant_result"))
            .map(constantResult -> constantResult.get(0))
            .map(Hex::decode)
            .map(Hex::toHexString)
            .map(str -> new BigInteger(str, 16))
            .map(BigInteger::intValue)
            .orElse(0);
  }

  // todo 1. rename  2. check handle exception when blance is 0
  public static double getTradePriceWithTRX(TradePair pair) throws Exception {
    log.info("Using pool addr: {} | trc20 addr: {}", pair.getPoolAddr(), pair.getTrc20Addr());
    return getTradePriceWithTRX(pair.getPoolAddr(), pair.getTrc20Addr());
  }

  public static double getTradePriceWithTRX(String poolAddr, String trc20Addr) throws Exception {
    // 1. get trx balance
    BigDecimal trxBalance = new BigDecimal(getTRXBalance(poolAddr));
    trxBalance = trxBalance.divide(new BigDecimal(TRX_DECIMAL_STR), 4, RoundingMode.HALF_UP);
    log.info("trxBalance: {}", trxBalance);
    // 2. get trc20 decimal
    int decimals = getDecimal(trc20Addr);
    StringBuilder strDecimals = new StringBuilder("1");
    while (--decimals >= 0) {
      strDecimals.append("0");
    }
    // 3. get trc20 balance
    BigDecimal trc20balance = new BigDecimal(balanceOf(poolAddr, trc20Addr));
    trc20balance = trc20balance.divide(new BigDecimal(strDecimals.toString()), 4, RoundingMode.HALF_UP);
    log.info("trxBalance: {}", trc20balance);

    return trxBalance.divide(trc20balance, 8, RoundingMode.HALF_UP).doubleValue();
  }

  public enum TradePair {
    JUST_TRX("TCFLL5dx5ZJdKnWuesXxi1VPwjLVmWZZy9", "TYukBQZ2XXCcRCReAUguyXncCWNY9CEiDQ"),
    SUN_TRX("TKkeiboTkxXKJpbmVFbv4a8ov5rAfRDMf9", "TUEYcyPAqc4hTg1fSuBCPc18vGWcJDECVw"),
    WIN_TRX("TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7", "TYN6Wh11maRfzgG7n5B6nM5VW1jfGs9chu"),
    DICE_TRX("TKttnV3FSY1iEoAwB4N52WK2DxdV94KpSd", "TJmTeYk5zmg8pNPGYbDb2psadwVLYDDYDr"),
    BTC_TRX("TN3W4H6rK2ce4vX9YnFQHwKENnHjoxb3m9", "TKAtLoCB529zusLfLVkGvLNis6okwjB7jf"),
    USDJ_TRX("TMwFHYXLJaRUPeW6421aqXL4ZEzPRFGkGT", "TQcia2H2TU3WrFk9sKtdK9qCfkW8XirfPQ"),
    USDT_TRX("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE");

    private String trc20Addr; // the first trc20 addr
    private String poolAddr;    // the trade pair addr in justswap

    TradePair(String trc20Addr, String poolAddr) {
      this.trc20Addr = trc20Addr;
      this.poolAddr = poolAddr;
    }

    public String getTrc20Addr() {
      return trc20Addr;
    }

    public String getPoolAddr() {
      return poolAddr;
    }
  }
}
