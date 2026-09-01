import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { friendApi } from '../api/friendApi'
import { friendKeys, friendListQueryOptions, friendRequestsQueryOptions } from '../api/friendQueries'

export function useFriends() {
  return useQuery(friendListQueryOptions())
}

export function useFriendRequests(direction: 'incoming' | 'outgoing') {
  return useQuery(friendRequestsQueryOptions(direction))
}

function useFriendMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: friendKeys.all }),
  })
}

export function useSendFriendRequest() { return useFriendMutation(friendApi.send) }
export function useAcceptFriendRequest() { return useFriendMutation(friendApi.accept) }
export function useRejectFriendRequest() { return useFriendMutation(friendApi.reject) }
export function useRemoveFriend() { return useFriendMutation(friendApi.remove) }
