package com.tron.client;

import static com.tron.common.Constant.ONE_HOUR;
import static com.tron.common.Constant.ONE_MINUTE;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.tron.OracleApplication;
import com.tron.common.Constant;
import com.tron.job.JobCache;
import com.tron.job.JobSubscriber;
import com.tron.keystore.KeyStore;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.tron.web.entity.Head;
import com.tron.web.entity.TronTx;
import org.junit.BeforeClass;
import org.junit.Test;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.tron.common.parameter.CommonParameter;

public class OracleClientTest {

  @BeforeClass
  public static void init() throws FileNotFoundException {
    KeyStore.initKeyStore("classpath:key.store");
    ConfigurableApplicationContext context = SpringApplication.run(OracleApplication.class, new String[]{});
    JobCache jobCache = context.getBean(JobCache.class);
    jobCache.run();
    JobSubscriber.setup();
  }

  @Test
  public void requestEventTest() throws IOException {
    String addr = "TCFLL5dx5ZJdKnWuesXxi1VPwjLVmWZZy9";
    Map<String, String> params = Maps.newHashMap();
    params.put("min_block_timestamp", Long.toString(System.currentTimeMillis() - ONE_HOUR));
    System.out.println(OracleClient.requestEvent(addr, params));
  }

  @Test
  public void fulfilTest() throws Exception {
    System.out.println(CommonParameter.getInstance()
            .getValidContractProtoThreadNum());
    CommonParameter.getInstance().setValidContractProtoThreadNum(1);
    FulfillRequest request = null;
    Constant.HTTP_EVENT_HOST = "nile.trongrid.io";
    Constant.FULLNODE_HOST = "api.nileex.io";
    if ("api.nileex.io".equals(Constant.FULLNODE_HOST)) {
      request = new FulfillRequest(
              "TNweaBP3y96ui2aR3yGPCTDAscoJ2hKR5E",
              "bf6263dad699d6ef3f80a204a92488776c55a703bbe08f118708179f1345a86d",
              new BigInteger("10000000000000000000000"),
              "TGpAFMZKd7rjjzz2E4WzYVKwBovWaFVcEQ",
              "6a9705b400000000000000000000000000000000000000000000000000000000",
              1600670994,
              "456");
      System.out.println(Hex.toHexString(new BigInteger("16").toByteArray()));
      TronTx tx = new TronTx();
      OracleClient.fulfil(request, tx);

    }
  }

  @Test
  public void vrfFulfillTest() throws Exception {
    System.out.println(CommonParameter.getInstance()
            .getValidContractProtoThreadNum());
    CommonParameter.getInstance().setValidContractProtoThreadNum(1);
    FulfillRequest vrfFulfillRequest = null;
    Constant.HTTP_EVENT_HOST = "nile.trongrid.io";
    Constant.FULLNODE_HOST = "api.nileex.io";

    String contractAddr = "TUeVYd9ZYeKh87aDA9Tp7F5Ljc47JKC37x";
    String requestId = "a5ea08864f4ff9dc8c44f3ed428276d81e07b043e7432abd444ec561f8df5e38";
    String proof = "4e6bda4373bea59ec613b8721bcbb56222ab2ec10b18ba24ae369b7b74ab145224d509bc2778e6d1c8a093522ba7f9b6669a9aef57d2231f856e4b594ad5f4ac46c444e90e27ae8b760f17ca594fa6c1d5093a8f9b70ec17009c8c6797c6b0dcc58344b20add0c06678ac16d5371c00ccf27f7bb5e1b0172e7a67616bc477afbe5046abd3c23e1f1fd79798ffb9c32453cc934a5a204c2a60baf6697dafd2baadd3e4f540d82f6547d6e0472d5d7e687e10f9747c5a765f5d12be669996ffe5eee5dcec71ada05f299c55bb900cc923bc206a32c18c5f9814aa11488ac6fcdea0000000000000000000000002c9e1058b3950c599c2f05e26fecd265b39cf9e45a19dc749b9b608b10ea3fb5817c19c29e86f38e9d9f59120094a727bb3be14a5b986255a1901f957d311c0169f864d34d8cdabfe704b0ba507398ab6cd21034ee79c94324e52fde454e5b1acda999c9427422b020886d19bea3e12ad650e3c1a1a7c1a36f6e0e291267205612901b9e8cd808d3fc16739aec24d5e83b95b8ec24d56002c00539acfe0db7d0997bc90461110794f79ca36c3b77f59f116a7ac50000000000000000000000000000000000000000000000000000000000ff7b5c";

    if ("api.nileex.io".equals(Constant.FULLNODE_HOST)) {
      vrfFulfillRequest = new FulfillRequest(
              contractAddr,
              requestId,
              new BigInteger("12"),
              "",
              "",
              0,
              proof);
      TronTx tx = new TronTx();
      OracleClient.vrfFulfil(vrfFulfillRequest,tx);
      assert !(tx.getSurrogateId().startsWith("abc")  && (tx.getSurrogateId().length() == 13)) :"trigger contract failure";
      assert !(Strings.isNullOrEmpty(tx.getSurrogateId())):"trigger contract failure";
      System.out.println("transaction ID:"+tx.getSurrogateId());
    }
  }

  @Test
  public void getMinBlockTimestampTest() throws Exception {
    boolean result;
    Long expetedTimestamp;
    Long maxTimestamp;
    Long minTimestamp;
    Long timestamp;
    System.out.println(CommonParameter.getInstance()
            .getValidContractProtoThreadNum());
    CommonParameter.getInstance().setValidContractProtoThreadNum(1);
    String addr = "TUeVYd9ZYeKh87aDA9Tp7F5Ljc47JKC37x";  //VRFCoordinator address
    List<Head> hisHead = JobSubscriber.jobRunner.headService.getByAddress(addr);

    //1:
    String filterEvent1 = "VRFRequest";
    Map<String, String> params1 = Maps.newHashMap();
    params1.put("event_name", filterEvent1);
    params1.put("order_by", "block_timestamp,asc");

    result = OracleClient.getMinBlockTimestamp(addr, filterEvent1, params1);
    assert (result) : "getMinBlockTimestampTest failed";
    if (hisHead == null || hisHead.size() == 0) {
      maxTimestamp = System.currentTimeMillis() - ONE_MINUTE;
      minTimestamp = System.currentTimeMillis() - ONE_MINUTE-5;
      timestamp = Long.valueOf(params1.get("min_block_timestamp"),10);
      assert ((timestamp>minTimestamp) && (timestamp <= maxTimestamp)) : "the MinBlockTimestampTest is wrong";
    }
    else{
      expetedTimestamp = hisHead.get(0).getBlockTimestamp();
      timestamp = Long.valueOf(params1.get("min_block_timestamp"),10);
      assert (timestamp.equals(expetedTimestamp) ) : "the MinBlockTimestampTest is wrong";
    }

    //2:
    String filterEvent2 = "OracleRequest";
    Map<String, String> params2 = Maps.newHashMap();
    params2.put("event_name", filterEvent2);
    params2.put("order_by", "block_timestamp,asc");
    result = OracleClient.getMinBlockTimestamp(addr, filterEvent2, params2);
    assert (result) : "getMinBlockTimestampTest failed";
    maxTimestamp = System.currentTimeMillis() - ONE_MINUTE;
    minTimestamp = System.currentTimeMillis() - ONE_MINUTE-5;
    timestamp = Long.valueOf(params2.get("min_block_timestamp"),10);
    assert ((timestamp>minTimestamp) && (timestamp <= maxTimestamp)) : "the MinBlockTimestampTest is wrong";
    //System.out.println("currentTimeMillis is:" + Long.toString(System.currentTimeMillis()));
    //3:
    String filterEvent3 = "NewRound";
    Map<String, String> params3 = Maps.newHashMap();
    params3.put("event_name", filterEvent3);
    params3.put("order_by", "block_timestamp,asc");
    result = OracleClient.getMinBlockTimestamp(addr, filterEvent3, params3);
    assert (result) : "getMinBlockTimestampTest failed";
    maxTimestamp = System.currentTimeMillis() - ONE_MINUTE;
    minTimestamp = System.currentTimeMillis() - ONE_MINUTE-5;
    timestamp = Long.valueOf(params3.get("min_block_timestamp"),10);
    assert ((timestamp>minTimestamp) && (timestamp <= maxTimestamp)) : "the MinBlockTimestampTest is wrong";
    //System.out.println("currentTimeMillis is:" + Long.toString(System.currentTimeMillis()));
    //4:
    String filterEvent4 = "TESTRequest";
    Map<String, String> params4 = Maps.newHashMap();
    params4.put("event_name", filterEvent4);
    params4.put("order_by", "block_timestamp,asc");
    result = OracleClient.getMinBlockTimestamp(addr, filterEvent4, params4);
    assert (!result) : "getMinBlockTimestampTest failed";
  }

}
