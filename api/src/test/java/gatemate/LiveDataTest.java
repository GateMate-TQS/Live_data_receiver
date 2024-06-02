package gatemate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ExtendWith(MockitoExtension.class)
public class LiveDataTest {

    private static final Logger logger = LoggerFactory.getLogger(LiveDataTest.class);

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private LiveData liveData;

    private String apiKey = "69e12faf210f5b4c4588af381286a5c8";

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(liveData, "apiKey", apiKey);
    }

    @Test
    public void fetchDataAndSendToQueue_Success() {
        String mockResponse = "{\"data\": [{\"flight_date\": \"2023-11-23\", \"flight_status\": \"scheduled\"}]}"; 
        String expectedUrlActive = "http://api.aviationstack.com/v1/flights?access_key=" + apiKey + "&dep_icao=LPPT&flight_status=active";
        String expectedUrlScheduled = "http://api.aviationstack.com/v1/flights?access_key=" + apiKey + "&dep_icao=LPPT&flight_status=scheduled";
        logger.info("Expected URL for active: " + expectedUrlActive);
        logger.info("Expected URL for scheduled: " + expectedUrlScheduled);
        
        when(restTemplate.getForEntity(expectedUrlActive, String.class))
                .thenReturn(ResponseEntity.ok(mockResponse));
        when(restTemplate.getForEntity(expectedUrlScheduled, String.class))
                .thenReturn(ResponseEntity.ok(mockResponse));
        
        // Act
        liveData.fetchDataAndSendToQueue();

        // Assert
        verify(restTemplate, times(1)).getForEntity(eq(expectedUrlActive), eq(String.class)); 
        verify(restTemplate, times(1)).getForEntity(eq(expectedUrlScheduled), eq(String.class)); 
        verify(rabbitTemplate, times(2)).convertAndSend(eq("flight-data"), eq(mockResponse));
        logger.info("Test passed");
    }


    @Test
    public void testFetchDataAndSendToQueue_HttpClientErrorException() {
        // Mock the API response to throw HttpClientErrorException

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        // Call the method under test
        liveData.fetchDataAndSendToQueue();

        // Verify that data is not sent to the queue
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    public void testFetchDataAndSendToQueue_HttpServerErrorException() {
        // Mock the API response to throw HttpServerErrorException
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // Call the method under test
        liveData.fetchDataAndSendToQueue();

        // Verify that data is not sent to the queue
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    public void testFetchDataAndSendToQueue_UnexpectedException() {
        // Mock the API response to throw an unexpected exception
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Call the method under test
        liveData.fetchDataAndSendToQueue();

        // Verify that data is not sent to the queue
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    public void testQueueCreation() {
        // Verify that the queue is created with the correct name
        Queue queue = liveData.queue();
        assertEquals("flight-data", queue.getName());
    }
}