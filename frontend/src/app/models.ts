export interface Balance {
  queryDate: string;
  total: number;
  unvested: number;
  vested: number;
  exercised: number;
  pendingReserved: number;
  exercisable: number;
}

export interface VestingNode {
  seqNo: number;
  vestDate: string;
  scheduledQuantity: number;
  cliffNode: boolean;
  conditional: boolean;
  conditionMetAt: string | null;
  effectiveVestDate: string | null;
  vestedAsOf: boolean;
}

export interface ExerciseRequest {
  id: number;
  requestNo: string;
  quantity: number;
  requestDate: string;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED';
  confirmedDate: string | null;
  cancelledDate: string | null;
}

export interface GrantSummary {
  id: number;
  grantName: string;
  grantee: string;
  planName: string;
  grantDate: string;
  totalQuantity: number;
  cliffMonths: number;
  vestingMonths: number;
}

export interface GrantTimeline extends GrantSummary {
  grantId: number;
  balance: Balance;
  nodes: VestingNode[];
  requests: ExerciseRequest[];
}

export interface CreateGrantPayload {
  grantName: string;
  grantee: string;
  grantDate: string;
  totalQuantity: number;
  cliffMonths: number;
  vestingMonths: number;
  planName: string;
  conditionalMonths: number[];
}
