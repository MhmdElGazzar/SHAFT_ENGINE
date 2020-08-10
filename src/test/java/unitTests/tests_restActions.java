package unitTests;

import com.shaft.api.RestActions;
import com.shaft.api.RestActions.RequestType;
import com.shaft.validation.Assertions;
import com.shaft.validation.Assertions.AssertionComparisonType;
import com.shaft.validation.Assertions.AssertionType;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.List;

public class tests_restActions {

    @Test
    public void getPostsAndAssertBodyForSpecificTitle() {
        String serviceURI = "https://jsonplaceholder.typicode.com/";

        Response posts = (new RestActions(serviceURI)).performRequest(RequestType.GET, 200, "posts");
        List<Object> postsList = RestActions.getResponseJSONValueAsList(posts, "");
        postsList.forEach(post -> {
            if (RestActions.getResponseJSONValue(post, "title").equals("qui est esse")) {
                Assertions.assertEquals("qui neque nisi nulla", RestActions.getResponseJSONValue(post, "body"),
                        AssertionComparisonType.CONTAINS, AssertionType.POSITIVE);
            }
        });
    }

    @Test
    public void getAnimals() {
        RestActions api = new RestActions("https://cat-fact.herokuapp.com");
        Response response = api.performRequest(RequestType.GET, 200, "/facts/random?animal_type=cat&amount=1");
        String actualResponse = RestActions.getResponseJSONValue(response, "text");
        Assertions.assertEquals("", actualResponse, AssertionComparisonType.EQUALS, AssertionType.NEGATIVE);
    }
}
