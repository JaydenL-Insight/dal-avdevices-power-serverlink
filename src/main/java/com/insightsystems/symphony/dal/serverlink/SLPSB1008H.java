package com.insightsystems.symphony.dal.serverlink;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.HttpCommunicator;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.util.Collections.singletonList;

public class SLPSB1008H extends HttpCommunicator implements Monitorable, Pingable, Controller {
    private String[][] pduInfo = new String[8][2];
    private static final String DALVersion = "v1.0";
    /*
        pduInfo[outlets,info]
                 |        |
             PDU outlet   |
               0 - 7      |
                        0 or 1
               outletName    outletState
     */

    @Override
    protected void internalInit() throws Exception {
        //Set login values to device hardcoded
        setLogin("snmp");
        setPassword("1234");
        this.setProtocol("http");
        this.setPort(80);
        this.setAuthenticationScheme(AuthenticationScheme.Basic);
        this.setContentType("application/xml");
        super.internalInit();

    }

    @Override
    protected void authenticate() throws Exception {} //No additional authentication required. Basic Authentication.

    public List<Statistics> getMultipleStatistics() throws Exception {
        getNames();
        final String currentDraw = getStatus();
        ExtendedStatistics deviceStatistics = new ExtendedStatistics();
        Map<String,String> statistics = new HashMap<String,String>();
        Map<String,String> control = new HashMap<String,String>();

        for (int i = 0; i < pduInfo.length; i++){
            statistics.put(pduInfo[i][0],pduInfo[i][1]);
            control.put(pduInfo[i][0],"Toggle");
        }
        statistics.put("CurrentDraw",currentDraw);
        deviceStatistics.setStatistics(statistics);
        deviceStatistics.setControl(control);

        return singletonList(deviceStatistics);
    }

    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {
        if (cp == null)
            return;

        for (int i = 0;i<pduInfo.length; i++){
            if (pduInfo[i][0].equals(cp.getProperty())){
                switch (String.valueOf(cp.getValue())){
                    case "0":
                        controlOutlets(""+i,false);
                        break;
                    case "1":
                        controlOutlets(""+i,true);
                        break;
                    default: //in case control value is invalid for some reason.
                }
                break; //Already found name, no need to loop through the rest.
            }
        }

    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        String controlOn = "";
        String controlOff = "";
        for (ControllableProperty cp : list){
            if (cp == null)
                continue;

            for (int i = 0;i<pduInfo.length; i++){
                if (pduInfo[i][0].equals(cp.getProperty())){
                    switch ((int)cp.getValue()){
                        case 0:
                            controlOff += i;
                            break;
                        case 1:
                            controlOn += i;
                            break;
                        default:
                    }
                    break; //Already found name, no need to loop through the rest.
                }
            }
        }
        controlOutlets(controlOn,true);
        controlOutlets(controlOff,false);
    }

    private void getNames() throws Exception {
        String rawNames = this.doPost("/Getname.xml",""); //Get port status form the pdu
        rawNames = rawNames.replaceAll("<response>","").replaceAll("</response>","");
        for (int n=0;n < 8;n++){
            pduInfo[n][0] = regexFind(rawNames,"<na"+n+">([\\s\\w]*)(?:,[\\s\\w]*)*</na"+n+">?");
        }

    }

    private String getStatus() throws Exception {
        String devResponse = this.doPost("/status.xml", ""); //Get status from the pdu
        devResponse = regexFind(devResponse, "<pot0>([0-9,.]*)</pot0>");
        final String[] split = devResponse.split(",");
        for (int n = 0; n < 8; n++) {
            pduInfo[n][1] = split[n+10];
        }
        return split[2];
    }

    private void controlOutlets(String outletString,boolean requestedState) throws Exception{
        final char[] outlets = outletString.toCharArray();
        System.out.println(outletString);
        char[] control = {'0','0','0','0','0','0','0','0'};
        for (int i = 0; i < outlets.length; i++) {
            control[outlets[i]-'0'] = '1';
        }
        if (requestedState) {
            doPost("/ons.cgi?led=" + stringifyChars(control), "");
        } else {
            doPost("/offs.cgi?led=" + stringifyChars(control), "");
        }
    }

    private String regexFind(String sourceString,String regex){
        final Matcher matcher = Pattern.compile(regex).matcher(sourceString);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String stringifyChars(char[] chars){
        String output = "";
        for (char c : chars){
            output += c;
        }
        return output;
    }
    
    public static void main(String[] args) throws Exception {
        SLPSB1008H test = new SLPSB1008H();
        test.setHost("10.164.69.10");
        test.init();

        ExtendedStatistics res = (ExtendedStatistics)test.getMultipleStatistics().get(0);
        System.out.println("Statistics.");
        res.getStatistics().forEach((k,v)->{
            System.out.println(k + " : " + v);
        });
        System.out.println("Controls.");
        res.getControl().forEach((k,v)->{
            System.out.println(k + " : " + v);
        });
        ControllableProperty cp = new ControllableProperty();
        cp.setValue(1);
        cp.setProperty("PIR");
        test.controlProperty(cp);
        cp.setValue('1');
        test.controlProperty(cp);
        cp.setValue("1");
        test.controlProperty(cp);
        Thread.sleep(5000);
        res = (ExtendedStatistics)test.getMultipleStatistics().get(0);
        res.getStatistics().forEach((k,v)->{
            System.out.println(k + " : " + v);
        });
    }
}
