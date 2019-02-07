package gen;

import connect.FileSFTP;
import field.PaymentHub;
import org.apache.log4j.Logger;
import org.beanio.BeanReader;
import org.beanio.StreamFactory;
import util.Constant;
import util.Util;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PaymentHubFileReader {

    final static Logger logger = Logger.getLogger(PaymentHubFileReader.class);

    public PaymentHubFileReader(Properties prop, StreamFactory factory, Connection con, String fileName) throws Exception {

        new FileSFTP(prop, 'D', fileName);

        logger.debug("Create local file");
        File file = new File(fileName);
        logger.debug("Mapping reader file from stream name is "+prop.getProperty("stream.name"));
        BeanReader in = factory.createReader(prop.getProperty("stream.name"), file);

        List<PaymentHub> paymentHubFiles = new ArrayList<>();
        logger.info("Reading file");
        try {
            Object record;
//            PaymentHubFooter footer = null;
            while ((record = in.read()) != null) {

                if ("record".equals(in.getRecordName())) {
                    PaymentHub paymentHub = (PaymentHub) record;

                    if (paymentHub.getRecordIndentifer().equals("D")) {
                        paymentHubFiles.add(paymentHub);
                    }

                }
//                else if ("trailer".equals(in.getRecordName())) {
//                    footer = (PaymentHubFooter) record;
//                    if (!footer.getRecordIndentifer().equals("T")){
//                        throw new Exception("Last Record is not of type T in the file.");
//                    }
//
//                }
            }

            if (paymentHubFiles.isEmpty())
                throw new Exception("File has no records");


        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e);
            return;
        }
        logger.info("Reading file complete!!");

        logger.debug("PreparedStatement : "+prop.getProperty("db.select"));
        PreparedStatement selectStmt = con.prepareStatement(prop.getProperty("db.select"));

        try {
            ResultSet rs = selectStmt.executeQuery();
            logger.debug("PreparedStatement executeQuery complete!!");

            logger.debug("PreparedStatement : "+prop.getProperty("db.update"));
            PreparedStatement updateStmt = con.prepareStatement(prop.getProperty("db.update"));

            int updateNum = 0;

            while (rs.next()) {

                try {
                    PaymentHub updateData = getUpdateObj(paymentHubFiles, rs);

                    if (updateData == null) {
                        logger.debug(Constant.SqlField.AcctNumber + " : " +
                                rs.getInt(Constant.SqlField.AcctNumber) + " no need to update");
                    } else {
                        int i = 0;

                        //set
                        updateStmt.setBigDecimal(++i, Util.strToBigDec(updateData.getAvailableBalance()));
                        updateStmt.setInt(++i, Integer.valueOf(updateData.getAccountProductCode()));
                        //Where
                        updateStmt.setString(++i, updateData.getAcctNumber());
                        updateStmt.executeQuery();

                        logger.info(Constant.SqlField.AcctNumber + " : " + updateData.getAcctNumber() +
                                " Update " + Constant.SqlField.AccountProductCode +
                                " from " + rs.getInt(Constant.SqlField.AccountProductCode) +
                                " to " + updateData.getAccountProductCode() +
                                " Update " + Constant.SqlField.AvailableBalance +
                                " from " + rs.getBigDecimal(Constant.SqlField.AvailableBalance) +
                                " to " + Util.strToBigDec(updateData.getAvailableBalance()) +
                                " Successfully Updated!!");

                        updateNum++;

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("Can't Update " + Constant.SqlField.AcctNumber + "=" +
                            rs.getLong(Constant.SqlField.AcctNumber) + " : " + e);
                }
            }

            if (updateNum <= 0) {
                logger.info("No Data Updated");
            }

//            updateStmt.executeBatch();

        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e);
        } finally {
            in.close();
            file.delete();
            logger.debug("Remove local file");
            con.close();
            logger.info("Database Connection close");
        }


    }

    private PaymentHub getUpdateObj(List<PaymentHub> list, ResultSet rs) throws Exception {

        for (PaymentHub obj : list) {

            Long oN = Long.valueOf(obj.getAcctNumber());
            Long rsN = rs.getLong(Constant.SqlField.AcctNumber);

            Integer oPC = Integer.valueOf(obj.getAccountProductCode());
            Integer rsPC = rs.getInt(Constant.SqlField.AccountProductCode);

            BigDecimal oB = Util.strToBigDec(obj.getAvailableBalance());
            BigDecimal rsB = rs.getBigDecimal(Constant.SqlField.AvailableBalance).setScale(2);

            if (oN.equals(rsN) && (!(oPC.equals(rsPC) && (oB.compareTo(rsB) == 0)))) {
                return obj;
            }

        }
        return null;
    }
}