import { useMemo, useRef, useState } from 'react';
import { Image, StyleSheet, View } from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { useNavigation } from '@react-navigation/native';
import { ActivityIndicator, Button, Surface, Text } from 'react-native-paper';
import { apiClient } from '../api/client';

type ScreenState = 'camera' | 'review';

type UploadUrlResponse = {
  id?: string;
  receiptId?: string;
  uploadUrl?: string;
  url?: string;
  presignedUrl?: string;
};

type ReceiptStatusResponse = {
  status?: string;
  extractedFields?: Record<string, unknown>;
  ocrData?: Record<string, unknown>;
  result?: Record<string, unknown>;
};

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const toStringOrEmpty = (value: unknown) => {
  if (value === null || value === undefined) {
    return '';
  }

  return String(value);
};

export default function ScanReceiptScreen() {
  const navigation = useNavigation();
  const cameraRef = useRef<CameraView | null>(null);

  const [permission, requestPermission] = useCameraPermissions();
  const [screenState, setScreenState] = useState<ScreenState>('camera');
  const [capturedPhotoUri, setCapturedPhotoUri] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [errorText, setErrorText] = useState<string | null>(null);

  const hasPermission = useMemo(() => permission?.granted ?? false, [permission?.granted]);

  const handleCapture = async () => {
    if (!cameraRef.current || isProcessing) {
      return;
    }

    setErrorText(null);

    try {
      const photo = await cameraRef.current.takePictureAsync({ quality: 0.8 });

      if (!photo?.uri) {
        setErrorText('Unable to capture photo. Please try again.');
        return;
      }

      setCapturedPhotoUri(photo.uri);
      setScreenState('review');
    } catch {
      setErrorText('Unable to capture photo. Please try again.');
    }
  };

  const handleRetake = () => {
    setCapturedPhotoUri(null);
    setScreenState('camera');
    setErrorText(null);
  };

  const handleUsePhoto = async () => {
    if (!capturedPhotoUri) {
      return;
    }

    setIsProcessing(true);
    setErrorText(null);

    try {
      const uploadInfoResponse = await apiClient.post<UploadUrlResponse>('/v1/receipts/upload-url');
      const uploadInfo = uploadInfoResponse.data;

      const receiptId = uploadInfo.id ?? uploadInfo.receiptId;
      const uploadUrl = uploadInfo.uploadUrl ?? uploadInfo.url ?? uploadInfo.presignedUrl;

      if (!receiptId || !uploadUrl) {
        throw new Error('Upload details are missing.');
      }

      const localFileResponse = await fetch(capturedPhotoUri);
      const fileBlob = await localFileResponse.blob();

      const uploadResponse = await fetch(uploadUrl, {
        method: 'PUT',
        headers: {
          'Content-Type': 'image/jpeg',
        },
        body: fileBlob,
      });

      if (!uploadResponse.ok) {
        throw new Error('Failed to upload receipt image.');
      }

      let receiptResponse: ReceiptStatusResponse | null = null;

      for (;;) {
        const statusResponse = await apiClient.get<ReceiptStatusResponse>(`/v1/receipts/${receiptId}`);
        receiptResponse = statusResponse.data;

        if ((receiptResponse.status ?? '').toUpperCase() === 'DONE') {
          break;
        }

        await delay(2000);
      }

      const extracted =
        receiptResponse?.extractedFields ?? receiptResponse?.ocrData ?? receiptResponse?.result ?? {};

      const rawTags = extracted.tags;
      const tagsArray = Array.isArray(rawTags)
        ? rawTags.map((tag) => toStringOrEmpty(tag).trim()).filter((tag) => tag.length > 0)
        : [];

      const prefill = {
        amount: toStringOrEmpty(extracted.amount ?? extracted.total),
        currency: toStringOrEmpty(extracted.currency || 'INR') || 'INR',
        merchantName: toStringOrEmpty(extracted.merchantName ?? extracted.merchant ?? extracted.vendor),
        paymentMethod: toStringOrEmpty(extracted.paymentMethod || 'UPI') || 'UPI',
        expenseDate: toStringOrEmpty(extracted.expenseDate ?? extracted.date),
        categoryId: toStringOrEmpty(extracted.categoryId),
        notes: toStringOrEmpty(extracted.notes),
        tags: tagsArray,
      };

      (navigation as unknown as { navigate: (route: string, params?: object) => void }).navigate(
        'Add',
        { prefill },
      );
    } catch {
      setErrorText('Failed to extract receipt details. Please retake and try again.');
    } finally {
      setIsProcessing(false);
    }
  };

  if (!permission) {
    return (
      <View style={styles.centeredContainer}>
        <ActivityIndicator />
      </View>
    );
  }

  if (!hasPermission) {
    return (
      <View style={styles.centeredContainer}>
        <Surface style={styles.permissionCard} elevation={1}>
          <Text style={styles.permissionText}>Camera permission is required to scan receipts.</Text>
          <Button mode="contained" onPress={requestPermission}>
            Grant Permission
          </Button>
        </Surface>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {screenState === 'camera' ? (
        <View style={styles.container}>
          <CameraView ref={cameraRef} style={styles.camera} facing="back" />

          <View style={styles.overlay} pointerEvents="none">
            <View style={styles.frame} />
          </View>

          <View style={styles.captureBar}>
            <Button
              mode="contained"
              onPress={handleCapture}
              disabled={isProcessing}
              contentStyle={styles.captureButtonContent}
            >
              Capture
            </Button>
          </View>
        </View>
      ) : (
        <View style={styles.reviewContainer}>
          {capturedPhotoUri ? <Image source={{ uri: capturedPhotoUri }} style={styles.previewImage} /> : null}

          <View style={styles.reviewActions}>
            <Button mode="outlined" onPress={handleRetake} disabled={isProcessing}>
              Retake
            </Button>
            <Button mode="contained" onPress={handleUsePhoto} loading={isProcessing} disabled={isProcessing}>
              Use Photo
            </Button>
          </View>
        </View>
      )}

      {errorText ? (
        <Surface style={styles.errorBanner} elevation={0}>
          <Text style={styles.errorText}>{errorText}</Text>
        </Surface>
      ) : null}

      {isProcessing ? (
        <View style={styles.processingOverlay}>
          <ActivityIndicator size="large" />
          <Text style={styles.processingText}>Extracting receipt details...</Text>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  centeredContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
    backgroundColor: '#f6f7fb',
  },
  permissionCard: {
    borderRadius: 12,
    padding: 16,
    gap: 10,
    width: '100%',
  },
  permissionText: {
    color: '#1f2937',
  },
  camera: {
    flex: 1,
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
  },
  frame: {
    width: '82%',
    height: '52%',
    borderWidth: 2,
    borderColor: '#f8fafc',
    borderRadius: 16,
    backgroundColor: 'transparent',
  },
  captureBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 30,
    alignItems: 'center',
  },
  captureButtonContent: {
    paddingHorizontal: 24,
    paddingVertical: 8,
  },
  reviewContainer: {
    flex: 1,
    backgroundColor: '#0f172a',
  },
  previewImage: {
    flex: 1,
    resizeMode: 'contain',
  },
  reviewActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 10,
    paddingHorizontal: 16,
    paddingVertical: 18,
    backgroundColor: '#111827',
  },
  errorBanner: {
    position: 'absolute',
    left: 12,
    right: 12,
    top: 16,
    borderRadius: 10,
    backgroundColor: '#fee2e2',
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  errorText: {
    color: '#b91c1c',
    fontWeight: '600',
  },
  processingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.55)',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  processingText: {
    color: '#fff',
    fontWeight: '600',
  },
});
