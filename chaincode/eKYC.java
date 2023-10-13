// @param - eKYC Chaincode in Java
// @author - Rohit Manshani
import org.hyperledger.fabric.shim.Chaincode;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.Chaincode.Response;
import com.google.gson.Gson;

public class EKYC extends ChaincodeBase {

    private int nextClientId = 1;
    private int nextFiId = 1;

    @Override
    public Response init(ChaincodeStub stub) {
        System.out.println("============= START : Initialize Ledger ===========");
        String[] clients = // Load initialClientData;
        String[] fis = // Load initialFIData;

        Gson gson = new Gson();

        for (String clientJSON : clients) {
            String newClientId = "CLIENT" + nextClientId;
            Map<String, Object> client = gson.fromJson(clientJSON, Map.class);
            client.put("docType", "client");
            stub.putStringState(newClientId, gson.toJson(client));
            System.out.println("Added <--> " + client);
            nextClientId++;

            String whoRegistered = (String) client.get("whoRegistered.ledgerUser");
            CompositeKey clientFiIndexKey = stub.createCompositeKey("clientId~fiId", new String[]{newClientId, whoRegistered});
            CompositeKey fiClientIndexKey = stub.createCompositeKey("fiId~clientId", new String[]{whoRegistered, newClientId});
            stub.putStringState(clientFiIndexKey.toString(), "");
            stub.putStringState(fiClientIndexKey.toString(), "");
        }

        for (String fiJSON : fis) {
            String fiId = "FI" + nextFiId;
            Map<String, Object> fi = gson.fromJson(fiJSON, Map.class);
            fi.put("docType", "fi");
            stub.putStringState(fiId, gson.toJson(fi));
            System.out.println("Added <--> " + fi);
            nextFiId++;
        }

        System.out.println("============= END : Initialize Ledger ===========");
        return newSuccessResponse();
    }

    @Override
    public Response invoke(ChaincodeStub stub) {
        String function = stub.getFunction();
        String[] args = stub.getParameters();
        if (function.equals("createClient")) {
            return createClient(stub, args);
        } else if (function.equals("getClientData")) {
            return getClientData(stub, args);
        } else if (function.equals("getFinancialInstitutionData")) {
            return getFinancialInstitutionData(stub, args);
        } else if (function.equals("approve")) {
            return approve(stub, args);
        } else if (function.equals("remove")) {
            return remove(stub, args);
        } else if (function.equals("getRelationByClient")) {
            return getRelationByClient(stub, args);
        } else if (function.equals("getRelationByFi")) {
            return getRelationByFi(stub, args);
        } else if (function.equals("queryAllData")) {
            return queryAllData(stub, args);
        }
        return newErrorResponse("Invalid function: " + function);
    }

    private Response createClient(ChaincodeStub stub, String[] args) {
        System.out.println("============= START : Create client ===========");
        if (args.length != 1) {
            return newErrorResponse("Incorrect number of arguments. Expecting 1");
        }

        Gson gson = new Gson();
        Map<String, Object> clientData = gson.fromJson(args[0], Map.class);
        String callerId = getCallerId(stub);

        if (!clientData.get("whoRegistered.ledgerUser").equals(callerId)) {
            return newErrorResponse("Caller is not who registered the client");
        }

        clientData.put("docType", "client");
        String newClientId = "CLIENT" + nextClientId;
        nextClientId++;
        stub.putStringState(newClientId, gson.toJson(clientData));

        CompositeKey clientFiIndexKey = stub.createCompositeKey("clientId~fiId", new String[]{newClientId, callerId});
        CompositeKey fiClientIndexKey = stub.createCompositeKey("fiId~clientId", new String[]{callerId, newClientId});
        stub.putStringState(clientFiIndexKey.toString(), "");
        stub.putStringState(fiClientIndexKey.toString(), "");

        System.out.println("============= END : Create client ===========");
        return newSuccessResponse(newClientId);
    }

    private Response getClientData(ChaincodeStub stub, String[] args) {
        if (args.length != 2) {
            return newErrorResponse("Incorrect number of arguments. Expecting 2");
        }

        String clientId = args[0];
        String fieldsStr = args[1];
        Gson gson = new Gson();

        String clientJSON = stub.getStringState(clientId);
        if (clientJSON.isEmpty()) {
            return newErrorResponse("Client does not exist or does not have data");
        }

        Map<String, Object> clientData = gson.fromJson(clientJSON, Map.class);
        String callerId = getCallerId(stub);

        if (!clientData.get("whoRegistered.ledgerUser").equals(callerId)) {
            String relationsJSON = stub.getStringState(getRelationKeyByFi(stub, callerId));
            List<String> relations = gson.fromJson(relationsJSON, List.class);

            if (!relations.contains(clientId)) {
                return newErrorResponse("Caller is not approved to access this client data");
            }
        }

        Map<String, Object> result = new HashMap<>();
        String[] fields = fieldsStr.split(",");
        for (String field : fields) {
            field = field.trim();
            if (clientData.containsKey(field)) {
                result.put(field, clientData.get(field));
            }
        }
        return newSuccessResponse(gson.toJson(result));
    }

    private Response getFinancialInstitutionData(ChaincodeStub stub, String[] args) {
        String callerId = getCallerId(stub);
        String fiJSON = stub.getStringState(callerId);
        if (fiJSON.isEmpty()) {
            return newErrorResponse("Financial Institution data not found");
        }
        return newSuccessResponse(fiJSON);
    }

    private Response approve(ChaincodeStub stub, String[] args) {
        System.out.println("======== START : Approve financial institution for client data access ==========");
        if (args.length != 2) {
            return newErrorResponse("Incorrect number of arguments. Expecting 2");
        }

        String clientId = args[0];
        String fiId = args[1];

        boolean isWhoRegistered = isWhoRegistered(stub, clientId);
        if (!isWhoRegistered) {
            return newErrorResponse("Caller is not who registered the client");
        }

        CompositeKey clientFiIndexKey = stub.createCompositeKey("clientId~fiId", new String[]{clientId, fiId});
        CompositeKey fiClientIndexKey = stub.createCompositeKey("fiId~clientId", new String[]{fiId, clientId});

        if (clientFiIndexKey.isEmpty() || fiClientIndexKey.isEmpty()) {
            return newErrorResponse("Composite keys are null");
        }

        stub.putStringState(clientFiIndexKey.toString(), "");
        stub.putStringState(fiClientIndexKey.toString(), "");

        System.out.println("======== END : Approve financial institution for client data
