package ia.example.mcpserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTests {

    @Mock
    private RestClient restClient;

    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        weatherService = new WeatherService();
        ReflectionTestUtils.setField(weatherService, "restClient", restClient);
    }

    @Test
    void shouldReturnWeatherForecastWhenRequestSucceeds() {
        double latitude = 47.6062;
        double longitude = -122.3321;

        WeatherService.Points.Props pointsProps = new WeatherService.Points.Props("https://api.weather.gov/gridpoints/SEW/123,456/forecast");
        WeatherService.Points points = new WeatherService.Points(pointsProps);

        List<WeatherService.Forecast.Period> periods = new ArrayList<>();
        periods.add(new WeatherService.Forecast.Period(
                1, "Tonight",
                "2024-05-08T20:00:00Z", "2024-05-09T06:00:00Z",
                false, 55, "F", null, null,
                "10 mph", "SW",
                "https://api.weather.gov/icons/land/night/rain,20",
                "Rainy",
                "Rain likely. Low around 55."
        ));

        WeatherService.Forecast.Props forecastProps = new WeatherService.Forecast.Props(periods);
        WeatherService.Forecast forecast = new WeatherService.Forecast(forecastProps);

        mockRestClientChain(points, forecast);

        String result = weatherService.getWeatherForecastByLocation(latitude, longitude);

        assertNotNull(result);
        assertTrue(result.contains("Tonight"));
        assertTrue(result.contains("55"));
        assertTrue(result.contains("Rainy"));
        assertTrue(result.contains("Rain likely"));
    }

    @Test
    void shouldReturnMultiplePeriodsForecastSuccessfully() {
        double latitude = 40.7128;
        double longitude = -74.0060;

        List<WeatherService.Forecast.Period> periods = new ArrayList<>();
        periods.add(new WeatherService.Forecast.Period(
                1, "Today",
                "2024-05-08T12:00:00Z", "2024-05-08T18:00:00Z",
                true, 68, "F", null, null,
                "5 mph", "N",
                "https://api.weather.gov/icons/land/day/sunny",
                "Sunny",
                "Sunny all day"
        ));
        periods.add(new WeatherService.Forecast.Period(
                2, "Tonight",
                "2024-05-08T18:00:00Z", "2024-05-09T06:00:00Z",
                false, 58, "F", null, null,
                "8 mph", "NW",
                "https://api.weather.gov/icons/land/night/clear",
                "Clear",
                "Clear skies tonight"
        ));

        WeatherService.Forecast.Props forecastProps = new WeatherService.Forecast.Props(periods);
        WeatherService.Forecast forecast = new WeatherService.Forecast(forecastProps);

        WeatherService.Points.Props pointsProps2 = new WeatherService.Points.Props("https://api.weather.gov/gridpoints/OKX/123,456/forecast");
        WeatherService.Points points2 = new WeatherService.Points(pointsProps2);

        mockRestClientChain(points2, forecast);

        String result = weatherService.getWeatherForecastByLocation(latitude, longitude);

        assertNotNull(result);
        assertTrue(result.contains("Today"));
        assertTrue(result.contains("Tonight"));
        assertTrue(result.contains("68"));
        assertTrue(result.contains("58"));
    }

    @Test
    void shouldReturnAlertsSuccessfully() {
        String state = "NY";

        WeatherService.Alert.Properties props1 = new WeatherService.Alert.Properties(
                "Winter Storm Warning",
                "New York",
                "Severe",
                "Heavy snow expected",
                "Stay indoors"
        );
        WeatherService.Alert.Feature feature1 = new WeatherService.Alert.Feature(props1);

        WeatherService.Alert.Properties props2 = new WeatherService.Alert.Properties(
                "Tornado Watch",
                "Western NY",
                "Moderate",
                "Tornadoes possible",
                "Take shelter"
        );
        WeatherService.Alert.Feature feature2 = new WeatherService.Alert.Feature(props2);

        List<WeatherService.Alert.Feature> features = new ArrayList<>();
        features.add(feature1);
        features.add(feature2);

        WeatherService.Alert alert = new WeatherService.Alert(features);

        mockRestClientForAlerts(alert);

        String result = weatherService.getAlerts(state);

        assertNotNull(result);
        assertTrue(result.contains("Winter Storm Warning"));
        assertTrue(result.contains("New York"));
        assertTrue(result.contains("Severe"));
        assertTrue(result.contains("Tornado Watch"));
    }

    @Test
    void shouldReturnMultipleAlertsWithCorrectFormatting() {
        String state = "CA";

        WeatherService.Alert.Properties props = new WeatherService.Alert.Properties(
                "Heat Advisory",
                "Southern California",
                "Moderate",
                "Extreme heat conditions",
                "Drink plenty of water"
        );
        WeatherService.Alert.Feature feature = new WeatherService.Alert.Feature(props);

        List<WeatherService.Alert.Feature> features = new ArrayList<>();
        features.add(feature);

        WeatherService.Alert alert = new WeatherService.Alert(features);

        mockRestClientForAlerts(alert);

        String result = weatherService.getAlerts(state);

        assertNotNull(result);
        assertTrue(result.contains("Event: Heat Advisory"));
        assertTrue(result.contains("Area: Southern California"));
        assertTrue(result.contains("Severity: Moderate"));
        assertTrue(result.contains("Description: Extreme heat conditions"));
        assertTrue(result.contains("Instructions: Drink plenty of water"));
    }

    @Test
    void shouldReturnEmptyStringWhenNoAlertsExist() {
        String state = "CO";

        WeatherService.Alert alert = new WeatherService.Alert(new ArrayList<>());

        mockRestClientForAlerts(alert);

        String result = weatherService.getAlerts(state);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleCorrectTemperatureUnitsInForecast() {
        double latitude = 51.5074;
        double longitude = -0.1278;

        WeatherService.Points.Props pointsProps = new WeatherService.Points.Props("https://api.weather.gov/gridpoints/LON/123,456/forecast");
        WeatherService.Points points = new WeatherService.Points(pointsProps);

        List<WeatherService.Forecast.Period> periods = new ArrayList<>();
        periods.add(new WeatherService.Forecast.Period(
                1, "Today",
                "2024-05-08T12:00:00Z", "2024-05-08T18:00:00Z",
                true, 72, "F", null, null,
                "10 mph", "E",
                "https://api.weather.gov/icons/land/day/partly_cloudy",
                "Partly Cloudy",
                "Partly cloudy today"
        ));

        WeatherService.Forecast.Props forecastProps = new WeatherService.Forecast.Props(periods);
        WeatherService.Forecast forecast = new WeatherService.Forecast(forecastProps);

        mockRestClientChain(points, forecast);

        String result = weatherService.getWeatherForecastByLocation(latitude, longitude);

        assertNotNull(result);
        assertTrue(result.contains("Temperature: 72 F"));
    }

    @SuppressWarnings("unchecked")
    private void mockRestClientChain(WeatherService.Points points, WeatherService.Forecast forecast) {
        var requestHeadersUriSpec1 = mock(RestClient.RequestHeadersUriSpec.class);
        var requestHeadersSpec1 = mock(RestClient.RequestHeadersSpec.class);
        var responseSpec1 = mock(RestClient.ResponseSpec.class);

        var requestHeadersUriSpec2 = mock(RestClient.RequestHeadersUriSpec.class);
        var requestHeadersSpec2 = mock(RestClient.RequestHeadersSpec.class);
        var responseSpec2 = mock(RestClient.ResponseSpec.class);

        // Premier appel à restClient.get() pour les points
        when(restClient.get())
                .thenReturn(requestHeadersUriSpec1)
                .thenReturn(requestHeadersUriSpec2);

        when(requestHeadersUriSpec1.uri(anyString(), anyDouble(), anyDouble()))
                .thenReturn(requestHeadersSpec1);
        when(requestHeadersSpec1.retrieve()).thenReturn(responseSpec1);
        when(responseSpec1.body(WeatherService.Points.class)).thenReturn(points);

        // Deuxième appel à restClient.get() pour la prévision
        when(requestHeadersUriSpec2.uri(anyString()))
                .thenReturn(requestHeadersSpec2);
        when(requestHeadersSpec2.retrieve()).thenReturn(responseSpec2);
        when(responseSpec2.body(WeatherService.Forecast.class)).thenReturn(forecast);
    }

    @SuppressWarnings("unchecked")
    private void mockRestClientForAlerts(WeatherService.Alert alert) {
        var requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        var requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        var responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), anyString()))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(WeatherService.Alert.class)).thenReturn(alert);
    }
}





