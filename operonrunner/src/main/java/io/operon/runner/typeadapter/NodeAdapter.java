package io.operon.runner.typeadapter;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.operon.runner.node.*;

public class NodeAdapter extends TypeAdapter<Node> {
    @Override
    public void write(JsonWriter out, Node value) {
        // Serialization is not required for this example, so this method can be left empty.
        // Implement serialization logic if necessary.
    }

    @Override
    public Node read(JsonReader in) throws IOException {
        Node node = null;
        String nodeClassName = null;
    
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            System.out.println("name = " + name);
            if (name.equals("nodeClassName")) {
                nodeClassName = in.nextString();
            } else if (name.equals("data")) {
                if (nodeClassName != null) {
                    if (nodeClassName.equals("FunctionCall")) {
                        //node = new FunctionCall();
                    } else if (nodeClassName.equals("FunctionRefInvoke")) {
                        //node = new FuctionRefInvoke();
                    }
                    // Add more conditions for other concrete classes if needed
                    // else if (nodeClassName.equals("AnotherNodeClass")) {
                    //     node = new AnotherNodeClass();
                    // }
                    
                    // Perform deserialization of the node object
                    // For simplicity, let's assume there is a deserialize method in each concrete class
                    
                    //node.deserialize(in);
                }
            } else {
                in.skipValue(); // Skip unexpected properties
            }
        }
        in.endObject();
    
        return node;
    }

}
