import { MaterialCommunityIcons } from '@expo/vector-icons';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { Provider as PaperProvider } from 'react-native-paper';
import type { ComponentProps } from 'react';
import ApprovalsScreen from '../screens/ApprovalsScreen';
import AddExpenseScreen from '../screens/AddExpenseScreen';
import HomeScreen from '../screens/HomeScreen';
import LoginScreen from '../screens/LoginScreen';
import RegisterScreen from '../screens/RegisterScreen';
import ScanReceiptScreen from '../screens/ScanReceiptScreen';
import SettingsScreen from '../screens/SettingsScreen';
import { apiClient } from '../api/client';
import { useAuthStore } from '../store/authStore';

type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
};

type MainTabParamList = {
  Home: undefined;
  Add: undefined;
  Scan: undefined;
  Approvals: undefined;
  Profile: undefined;
};

type MainStackParamList = {
  MainTabs: undefined;
};

type PendingApprovalsResponse =
  | Array<{ id?: string; approvalId?: string }>
  | {
      items?: Array<{ id?: string; approvalId?: string }>;
      approvals?: Array<{ id?: string; approvalId?: string }>;
    };

const queryClient = new QueryClient();

const AuthStack = createNativeStackNavigator<AuthStackParamList>();
const MainStack = createNativeStackNavigator<MainStackParamList>();
const Tab = createBottomTabNavigator<MainTabParamList>();
type MaterialIconName = ComponentProps<typeof MaterialCommunityIcons>['name'];

function AuthStackNavigator() {
  return (
    <AuthStack.Navigator>
      <AuthStack.Screen name="Login" component={LoginScreen} options={{ title: 'Login' }} />
      <AuthStack.Screen
        name="Register"
        component={RegisterScreen}
        options={{ title: 'Register' }}
      />
    </AuthStack.Navigator>
  );
}

function MainTabNavigator() {
  const role = useAuthStore((state) => state.user?.role?.toLowerCase() ?? 'employee');
  const isApprover = role === 'manager' || role === 'admin';

  const pendingApprovalsQuery = useQuery({
    queryKey: ['pending-approvals'],
    queryFn: async () => {
      const response = await apiClient.get<PendingApprovalsResponse>('/v1/approvals/pending');
      const payload = response.data;
      const items = Array.isArray(payload)
        ? payload
        : (payload.items ?? payload.approvals ?? []);

      return items.length;
    },
    enabled: isApprover,
    refetchInterval: 30000,
  });

  const pendingCount = pendingApprovalsQuery.data ?? 0;

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarIcon: ({ color, size }) => {
          const iconMap: Record<keyof MainTabParamList, MaterialIconName> = {
            Home: 'home',
            Add: 'plus-circle',
            Scan: 'camera',
            Approvals: 'check-circle',
            Profile: 'account',
          };

          return (
            <MaterialCommunityIcons name={iconMap[route.name]} size={size} color={color} />
          );
        },
      })}
    >
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Add" component={AddExpenseScreen} />
      <Tab.Screen name="Scan" component={ScanReceiptScreen} />
      <Tab.Screen
        name="Approvals"
        component={ApprovalsScreen}
        options={{ tabBarBadge: pendingCount > 0 ? pendingCount : undefined }}
      />
      <Tab.Screen name="Profile" component={SettingsScreen} />
    </Tab.Navigator>
  );
}

function MainStackNavigator() {
  return (
    <MainStack.Navigator>
      <MainStack.Screen
        name="MainTabs"
        component={MainTabNavigator}
        options={{ headerShown: false }}
      />
    </MainStack.Navigator>
  );
}

export default function AppNavigator() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  return (
    <QueryClientProvider client={queryClient}>
      <PaperProvider>
        <NavigationContainer>
          {isAuthenticated ? <MainStackNavigator /> : <AuthStackNavigator />}
        </NavigationContainer>
      </PaperProvider>
    </QueryClientProvider>
  );
}
