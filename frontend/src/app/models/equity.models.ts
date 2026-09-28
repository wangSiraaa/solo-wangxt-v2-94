/** 授予登记请求。 */
export interface RegisterGrantRequest {
  name: string;
  grantee: string;
  grantDate: string;
  totalShares: number;
  cliffMonths: number;
  vestingMonths: number;
  conditionalNodes?: { monthIndex: number; conditionLabel: string }[];
}

export interface Grant {
  id: number;
  name: string;
  grantee: string;
  grantDate: string;
  totalShares: number;
  cliffMonths: number;
  vestingMonths: number;
}

/** 四阶段数量——各自独立展示，不用一个余额覆盖所有阶段。 */
export interface Quantities {
  total: number;
  unvested: number;
  vested: number;
  exercised: number;
  pending: number;
  exercisable: number;
}

export interface VestingNodeView {
  monthIndex: number;
  nodeDate: string;
  shares: number;
  conditional: boolean;
  conditionLabel: string;
  conditionMet: boolean;
  vestedDate: string | null;
  vestedAsOf: boolean;
}

export interface ExerciseRequestView {
  id: number;
  quantity: number;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'NONE';
  requestedDate: string;
  confirmedDate: string | null;
  cancelledDate: string | null;
  occupiesAsOf: boolean;
}

export interface Snapshot {
  grant: Grant;
  asOfDate: string;
  quantities: Quantities;
  nodes: VestingNodeView[];
  requests: ExerciseRequestView[];
}

export interface ExerciseRequestBody {
  quantity: number;
  asOf?: string;
}
