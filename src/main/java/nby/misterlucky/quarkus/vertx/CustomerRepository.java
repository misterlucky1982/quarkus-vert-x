package nby.misterlucky.quarkus.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.*;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class CustomerRepository {
    @Inject
    Vertx vertx;

    @ConfigProperty(name = "file.path" )
    String path;

    List<Customer> customerList = new ArrayList();
    int counter;

    public int getNextCustomerId() {
        return counter++;
    }

    public List<Customer> findAll() {
        return customerList;
    }

    public Customer findCustomerById(Integer id) {
        for (Customer c:customerList) {
            if (c.getId().equals(id))  {
                return c;
            }
        }
        throw new CustomerException("Customer not found!");
    }

    public void updateCustomer(Customer customer) {
        Customer customerToUpdate = findCustomerById(customer.getId());
        customerToUpdate.setName(customer.getName());
        customerToUpdate.setSurname(customer.getSurname());
    }

    public void createCustomer(Customer customer) {


        customer.setId(getNextCustomerId());
        findAll().add(customer);
    }

    public void deleteCustomer(Integer customerId) {

        Customer c = findCustomerById(customerId);
        findAll().remove(c);
    }

    public CompletionStage<String> writeFile( ) {

        JsonArrayBuilder jsonArray = javax.json.Json.createArrayBuilder();

        for (Customer customer:customerList) {

            jsonArray.add(javax.json.Json.createObjectBuilder().
                     add("id", customer.getId())
                    .add("name", customer.getName())
                    .add("surname", customer.getSurname()).build());

        }

        JsonArray array = jsonArray.build();

        CompletableFuture<String> future = new CompletableFuture<>();

        vertx.fileSystem().writeFile(path, Buffer.buffer(array.toString()), handler -> {
            if (handler.succeeded()) {

                future.complete("Written JSON file in " +path);
            } else {
                System.err.println("Error while writing in file: " + handler.cause().getMessage());

            }
        });

        return future;
    }


    public CompletionStage<String> readFile() {
        // When complete, return the content to the client
        CompletableFuture<String> future = new CompletableFuture<>();

        long start = System.nanoTime();

        // Delay reply by 100ms
        vertx.setTimer(100, l -> {

            vertx.fileSystem().readFile(path, ar -> {
                if (ar.succeeded()) {
                    String response = ar.result().toString("UTF-8");
                    JsonReader reader = Json.createReader(new StringReader(response));
                    JsonArray array = reader.readArray();
                    array.forEach(
                            part-> {
                                JsonObject ob = part.asJsonObject();
                                Customer cst = new Customer();
                                cst.setId(ob.getInt("id"));
                                cst.setName(ob.getString("name"));
                                cst.setSurname(ob.getString("surname"));
                                customerList.add(cst);
                            }
                    );
                    future.complete(response);
                } else {
                    future.complete("Cannot read the file: " + ar.cause().getMessage());
                }
            });

        });
        return future;
    }
}
